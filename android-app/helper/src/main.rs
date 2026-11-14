//! Standalone Owly radio/AirDrop helper for the K20 Pro.
//!
//! Commands:
//!   scan <seconds> <channel>
//!   send <file> [target-mac] <channel> [name]
//!   session <channel> [name]   (long-lived; reads scan/send/quit on stdin)
//!   receive <directory> <port> <channel> [name]

use std::collections::HashMap;
use std::env;
use std::error::Error;
use std::io::{BufRead, BufReader};
use std::net::SocketAddrV6;
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::{Arc, Mutex};
use std::time::Duration;

use owly_airdrop::driver::mdns::Mdns;
use owly_airdrop::driver::receiver::{
    Options as ReceiverOptions, Receiver, ReceiverEvent, Request,
};
use owly_airdrop::driver::sender::{Event, Payload, Sender};
use owly_airdrop::driver::tls::Identity;
use owly_airdrop::proto::messages::Offer;
use owly_airdrop::proto::mdns::{
    BrowseEvent, Burst, Found, Service, RECEIVER_FLAGS, SUPPORTS_DVZIP,
};
use owly_airdrop::proto::receiver::Config as ReceiverConfig;
use owly_airdrop::proto::sender::Config as SenderConfig;
use owly_awdl::{Channel, Config, MacAddr, Presence, RadioConstraints};
use owly_radio::native::Native;
use owly_radio::radio::PeerInfo;
use tokio::sync::watch;
use tokio::time::{sleep, timeout, Instant, MissedTickBehavior};

const MONITOR: &str = "wlan0";
const AWDL_IFACE: &str = "awdl0";
const DEFAULT_CHANNEL: u8 = 44;
const AIRDROP_PORT: u16 = 8770;
const RECEIVER_PORT: u16 = 8771;
const SET_CHANNEL: &str = "/data/local/tmp/owly-app/owly-nl80211-set-channel";
const STATE_DIR: &str = "/data/local/tmp/owly-app/state";

#[tokio::main]
async fn main() -> Result<(), Box<dyn Error>> {
    let args: Vec<String> = env::args().collect();
    let command = args.get(1).map(String::as_str).unwrap_or("scan");
    match command {
        "scan" => {
            let seconds = args.get(2).and_then(|s| s.parse().ok()).unwrap_or(10);
            let channel = args.get(3).and_then(|s| s.parse().ok()).unwrap_or(DEFAULT_CHANNEL);
            scan(seconds, channel).await
        }
        "send" => {
            let path = args.get(2).ok_or("usage: helper send <file> [mac] <channel>")?;
            let target = args.get(3).filter(|s| s.as_str() != "-");
            let channel = args.get(4).and_then(|s| s.parse().ok()).unwrap_or(DEFAULT_CHANNEL);
            let name = args.get(5).cloned().unwrap_or_else(|| "Droidy K20".into());
            send(PathBuf::from(path), target.cloned(), channel, name).await
        }
        "session" => {
            let channel = args.get(2).and_then(|s| s.parse().ok()).unwrap_or(DEFAULT_CHANNEL);
            let name = args.get(3).cloned().unwrap_or_else(|| "Droidy K20".into());
            session(channel, name).await
        }
        "receive" => {
            let dir = PathBuf::from(args.get(2).ok_or("usage: helper receive <dir> <port> <channel>")?);
            let port = args.get(3).and_then(|s| s.parse().ok()).unwrap_or(RECEIVER_PORT);
            let channel = args.get(4).and_then(|s| s.parse().ok()).unwrap_or(DEFAULT_CHANNEL);
            let name = args.get(5).cloned().unwrap_or_else(|| "Droidy K20".into());
            receive(dir, port, channel, name).await
        }
        "help" | "--help" | "-h" => {
            println!("usage: helper scan <seconds> <channel> | send <file> [mac] <channel> [name] | session <channel> [name] | receive <dir> <port> <channel> [name]");
            Ok(())
        }
        other => Err(format!("unknown command: {other}").into()),
    }
}

fn channel_freq(channel: u8) -> Option<&'static str> {
    match channel {
        6 => Some("2437"),
        44 => Some("5220"),
        149 => Some("5745"),
        _ => None,
    }
}

fn start_name_sniffer(names: Arc<Mutex<HashMap<String, String>>>) -> Option<Child> {
    let mut child = Command::new("/data/local/tmp/owly-app/owly-monitor-full")
        .arg(MONITOR)
        .stdout(Stdio::piped())
        .stderr(Stdio::null())
        .spawn()
        .ok()?;
    let stdout = child.stdout.take()?;
    std::thread::spawn(move || {
        for line in BufReader::new(stdout).lines().flatten() {
            let Some(mac) = line.split("sa=").nth(1) else { continue };
            let mac = mac.split_whitespace().next().unwrap_or("").to_string();
            let Some(hex) = line.split("bytes=").nth(1) else { continue };
            let hex = hex.split_whitespace().next().unwrap_or("");
            let Some(bytes) = decode_hex(hex) else { continue };
            if let Some(name) = find_dnm(&bytes) {
                let mut map = names.lock().unwrap();
                if map.get(&mac) != Some(&name) {
                    println!("AWDL name: {mac} = {name}");
                    map.insert(mac, name);
                }
            }
        }
    });
    Some(child)
}

fn decode_hex(text: &str) -> Option<Vec<u8>> {
    if text.len() % 2 != 0 { return None; }
    let mut out = Vec::with_capacity(text.len() / 2);
    let b = text.as_bytes();
    for i in (0..b.len()).step_by(2) {
        let hi = (b[i] as char).to_digit(16)?;
        let lo = (b[i + 1] as char).to_digit(16)?;
        out.push(((hi << 4) | lo) as u8);
    }
    Some(out)
}

fn find_dnm(bytes: &[u8]) -> Option<String> {
    if let Some(name) = find_dnm_in_awdl(bytes) {
        return Some(name);
    }
    // Fallback when the monitor line contains only a raw TXT fragment.
    let needle = b"dnm=";
    for start in 0..bytes.len().saturating_sub(needle.len()) {
        if &bytes[start..start + needle.len()] != needle { continue; }
        let value_start = start + needle.len();
        let declared_end = start
            .checked_sub(1)
            .and_then(|i| bytes.get(i))
            .map(|len| start.saturating_add(*len as usize))
            .unwrap_or(value_start);
        let end = declared_end.max(value_start).min(bytes.len());
        if end > value_start {
            let name = String::from_utf8_lossy(&bytes[value_start..end])
                .trim_end_matches(|c: char| c == '\0' || c == 'V')
                .trim()
                .to_string();
            if name.len() >= 3 {
                return Some(name);
            }
        }
    }
    None
}

/// The iPhone's display name is a DNS-SD TXT string (`dnm=...`) inside the
/// AWDL Service Response TLV (type 2). Follow the same record layout as
/// Owly's `omdrop-awdl/awdl-af-parse.py`; do not scan printable bytes.
fn find_dnm_in_awdl(bytes: &[u8]) -> Option<String> {
    let category = [0x7f, 0x00, 0x17, 0xf2];
    let start = bytes.windows(4).position(|w| w == category)?;
    let body = &bytes[start..];
    if body.len() < 16 || body[4] != 0x08 {
        return None;
    }
    let mut i = 16usize;
    while i + 3 <= body.len() {
        let kind = body[i];
        let len = u16::from_le_bytes([body[i + 1], body[i + 2]]) as usize;
        let value_start = i + 3;
        let value_end = value_start + len;
        if value_end > body.len() {
            break;
        }
        if kind == 2 {
            if let Some(name) = dnm_from_service_response(&body[value_start..value_end]) {
                return Some(name);
            }
        }
        i = value_end;
    }
    None
}

fn dnm_from_service_response(value: &[u8]) -> Option<String> {
    let mut pos = 0usize;
    while pos + 5 <= value.len() {
        let name_len = u16::from_le_bytes([value[pos], value[pos + 1]]) as usize;
        if name_len < 3 {
            break;
        }
        // name_len counts the type byte, matching Owly's awdl-af-parse.py.
        let type_pos = pos + 2 + name_len - 1;
        if type_pos + 3 > value.len() {
            break;
        }
        let rtype = value[type_pos];
        let data_len = u16::from_be_bytes([value[type_pos + 1], value[type_pos + 2]]) as usize;
        let data_start = type_pos + 5;
        let data_end = data_start + data_len;
        if data_end > value.len() {
            break;
        }
        if rtype == 16 {
            if let Some(name) = dnm_from_txt(&value[data_start..data_end]) {
                return Some(name);
            }
        }
        pos = data_end;
    }
    None
}

fn dnm_from_txt(txt: &[u8]) -> Option<String> {
    let mut i = 0usize;
    while i < txt.len() {
        let len = txt[i] as usize;
        i += 1;
        if i + len > txt.len() {
            break;
        }
        let string = &txt[i..i + len];
        i += len;
        if string.starts_with(b"dnm=") {
            let name = String::from_utf8_lossy(&string[4..]).trim().to_string();
            if !name.is_empty() {
                return Some(name);
            }
        }
    }
    None
}

async fn read_hidden_mac() -> Result<MacAddr, Box<dyn Error>> {
    // Base mode: the monitor carries the AWDL address. The hidden wlan_inj*
    // STA is only the WMI TX tag, so do not use its MAC as the AWDL identity.
    for _ in 0..30 {
        if let Ok(kind) = std::fs::read_to_string("/sys/class/net/wlan0/type") {
            if kind.trim() == "803" {
                if let Ok(text) = std::fs::read_to_string("/sys/class/net/wlan0/address") {
                    let octets = text
                        .trim()
                        .split(':')
                        .map(|part| u8::from_str_radix(part, 16))
                        .collect::<Result<Vec<_>, _>>();
                    if let Ok(octets) = octets {
                        if octets.len() == 6 {
                            let mut mac = [0u8; 6];
                            mac.copy_from_slice(&octets);
                            return Ok(MacAddr(mac));
                        }
                    }
                }
            }
        }
        if let Ok(entries) = std::fs::read_dir("/sys/class/net") {
            for entry in entries.flatten() {
                let name = entry.file_name().to_string_lossy().into_owned();
                if !name.starts_with("wlan_inj") {
                    continue;
                }
                if let Ok(text) = std::fs::read_to_string(entry.path().join("address")) {
                    let octets = text
                        .trim()
                        .split(':')
                        .map(|part| u8::from_str_radix(part, 16))
                        .collect::<Result<Vec<_>, _>>();
                    if let Ok(octets) = octets {
                        if octets.len() == 6 {
                            let mut mac = [0u8; 6];
                            mac.copy_from_slice(&octets);
                            return Ok(MacAddr(mac));
                        }
                    }
                }
            }
        }
        sleep(Duration::from_millis(100)).await;
    }
    Err("monitor or injection vSTA did not appear under /sys/class/net".into())
}

fn sender_id(addr: MacAddr) -> String {
    addr.0.iter().map(|b| format!("{b:02x}")).collect()
}

fn print_peers(peers: &[PeerInfo], names: &HashMap<String, String>) {
    for peer in peers {
        let mac = peer.mac.to_string();
        let name = names.get(&mac).cloned().unwrap_or_else(|| peer.name.clone());
        println!(
            "PEER mac={mac} name={} devclass={} rssi={}",
            name,
            peer.devclass,
            peer.rssi.unwrap_or(0)
        );
    }
}

#[derive(Clone)]
struct KnownPeer {
    info: PeerInfo,
    name: String,
}

fn resolved_name(peer: &PeerInfo, names: &HashMap<String, String>) -> String {
    let mac = peer.mac.to_string();
    names.get(&mac).cloned().unwrap_or_else(|| peer.name.clone())
}

/// Emit one structured line per peer add/change and one PEER_GONE per removal.
/// The app consumes these whether or not a scan is running, so the nearby list
/// stays live for the whole session.
fn emit_peer_updates(
    known: &mut HashMap<String, KnownPeer>,
    peers: &[PeerInfo],
    names: &HashMap<String, String>,
) {
    let mut current: HashMap<String, KnownPeer> = HashMap::new();
    for peer in peers {
        let mac = peer.mac.to_string();
        let name = resolved_name(peer, names);
        let changed = known.get(&mac).map_or(true, |old| {
            old.name != name
                || old.info.devclass != peer.devclass
                || old.info.rssi != peer.rssi
        });
        if changed {
            println!(
                "PEER mac={mac} name={name} devclass={} rssi={}",
                peer.devclass,
                peer.rssi.unwrap_or(0)
            );
        }
        current.insert(
            mac,
            KnownPeer {
                info: peer.clone(),
                name,
            },
        );
    }
    for mac in known.keys() {
        if !current.contains_key(mac) {
            println!("PEER_GONE mac={mac}");
        }
    }
    *known = current;
}

async fn start_radio(channel: u8) -> Result<(Native, u32, MacAddr, watch::Receiver<Vec<PeerInfo>>), Box<dyn Error>> {
    if !std::path::Path::new("/dev/net/tun").exists()
        && !std::path::Path::new("/dev/tun").exists()
    {
        return Err("no TUN device; awdl0 cannot be created".into());
    }
    let channel = if channel_freq(channel).is_some() { channel } else { DEFAULT_CHANNEL };
    let _ = Command::new("ip").args(["link", "set", MONITOR, "up"]).status();
    let addr = read_hidden_mac().await?;
    println!("INJECT_MAC {addr:?}");
    if std::path::Path::new(SET_CHANNEL).exists() {
        if let Some(freq) = channel_freq(channel) {
            let _ = Command::new(SET_CHANNEL).args([MONITOR, freq]).status();
        }
    }

    let radio = RadioConstraints { channel: Channel(channel), presence: Presence::Fixed };
    let mut cfg = Config::new(addr, "droidy-k20", radio);
    cfg.rssi_threshold = Some(-85);
    cfg.max_frame = 2304;
    let (peers_tx, peers_rx) = watch::channel(Vec::new());
    let native = Native::start(MONITOR, AWDL_IFACE, cfg, peers_tx)
        .map_err(|e| format!("could not start AWDL on {MONITOR}: {e}"))?;
    let index = native.index;

    let _ = Command::new("ip")
        .args(["-6", "route", "replace", "fe80::/64", "dev", AWDL_IFACE])
        .output();
    let _ = Command::new("ip")
        .args(["-6", "rule", "add", "pref", "1000", "to", "fe80::/10", "lookup", "main"])
        .output();
    println!("RADIO_READY channel={channel} index={index}");
    Ok((native, index, addr, peers_rx))
}

async fn scan(seconds: u64, channel: u8) -> Result<(), Box<dyn Error>> {
    let (native, index, _addr, mut peers_rx) = start_radio(channel).await?;
    let names = Arc::new(Mutex::new(HashMap::new()));
    let mut sniffer = start_name_sniffer(names.clone());
    let mdns = Mdns::new(index);
    let mut browse = mdns.browse().await?;
    println!("SCAN_START seconds={seconds} channel={channel}");
    let deadline = Instant::now() + Duration::from_secs(seconds);
    loop {
        tokio::select! {
            event = browse.events.recv() => match event {
                Some(BrowseEvent::Found(found)) => println!(
                    "FOUND instance={:?} host={:?} addr={} port={} flags=0x{:x}",
                    found.instance, found.host, found.addr, found.port, found.flags
                ),
                Some(BrowseEvent::Lost(instance)) => println!("LOST instance={instance:?}"),
                None => break,
            },
            changed = peers_rx.changed() => {
                if changed.is_ok() {
                    let peers = peers_rx.borrow().clone();
                    println!("AWDL peers now: {} {:?}", peers.len(), peers);
                }
            },
            _ = sleep(deadline.saturating_duration_since(Instant::now())) => break,
        }
    }
    let peers = peers_rx.borrow().clone();
    let names = names.lock().unwrap().clone();
    print_peers(&peers, &names);
    println!("PEER_COUNT {}", peers.len());
    drop(browse);
    if let Some(child) = sniffer.as_mut() {
        let _ = child.kill();
        let _ = child.wait();
    }
    native.stop();
    Ok(())
}

async fn send(path: PathBuf, target: Option<String>, channel: u8, name: String) -> Result<(), Box<dyn Error>> {
    if !path.is_file() {
        return Err(format!("not a file: {}", path.display()).into());
    }
    rustls::crypto::ring::default_provider().install_default().ok();
    let (native, index, addr, mut peers_rx) = start_radio(channel).await?;
    let names = Arc::new(Mutex::new(HashMap::new()));
    let mut sniffer = start_name_sniffer(names.clone());
    let mdns = Mdns::new(index);
    let mut browse = mdns.browse().await?;
    let mut sender_config = SenderConfig::new(name, sender_id(addr));
    sender_config.ask_timeout = Duration::from_secs(90);
    let sender = Sender::new(sender_config)?;
    let mut known: HashMap<String, KnownPeer> = HashMap::new();
    let result = find_and_send(&sender, index, &mut browse, &mut peers_rx, &names, &mut known, &path, target.as_deref()).await;
    if let Some(child) = sniffer.as_mut() {
        let _ = child.kill();
        let _ = child.wait();
    }
    native.stop();
    result
}

async fn find_and_send(
    sender: &Sender,
    index: u32,
    browse: &mut owly_airdrop::driver::mdns::Browse,
    peers_rx: &mut watch::Receiver<Vec<PeerInfo>>,
    names: &Arc<Mutex<HashMap<String, String>>>,
    known: &mut HashMap<String, KnownPeer>,
    path: &Path,
    target_mac: Option<&str>,
) -> Result<(), Box<dyn Error>> {
    let deadline = Instant::now() + Duration::from_secs(120);
    let mut mdns_found: Option<Found> = None;
    let mut attempt = 0u32;
    let mut chosen: Option<(SocketAddrV6, u32, String)> = None;
    let mut waiting_logged = false;

    while Instant::now() < deadline && chosen.is_none() {
        loop {
            match browse.events.try_recv() {
                Ok(BrowseEvent::Found(found)) => {
                    println!(
                        "mDNS FOUND instance={:?} host={:?} addr={} port={} flags=0x{:x}",
                        found.instance, found.host, found.addr, found.port, found.flags
                    );
                    if target_mac.is_none() { mdns_found = Some(found); }
                }
                Ok(BrowseEvent::Lost(instance)) => println!("mDNS LOST instance={instance:?}"),
                Err(tokio::sync::mpsc::error::TryRecvError::Empty) => break,
                Err(tokio::sync::mpsc::error::TryRecvError::Disconnected) => break,
            }
        }
        let mut targets: Vec<(SocketAddrV6, u32, &str)> = Vec::new();
        if let Some(found) = &mdns_found {
            targets.push((SocketAddrV6::new(found.addr, found.port, 0, index), found.flags, "mDNS"));
        }
        let peers = peers_rx.borrow().clone();
        let names_snapshot = names.lock().unwrap().clone();
        emit_peer_updates(known, &peers, &names_snapshot);
        for peer in &peers {
            let mac = peer.mac.to_string();
            if let Some(target) = target_mac {
                if !mac.eq_ignore_ascii_case(target) { continue; }
            }
            let addr = SocketAddrV6::new(peer.mac.link_local(), AIRDROP_PORT, 0, index);
            if let Some(name) = names_snapshot.get(&mac) {
                println!("AWDL peer {mac} is {name:?}");
            }
            if !targets.iter().any(|(a, _, _)| *a == addr) {
                targets.push((addr, RECEIVER_FLAGS | SUPPORTS_DVZIP, "peer"));
            }
        }
        if peers.is_empty() {
            if !waiting_logged {
                println!("waiting for an AWDL peer...");
                waiting_logged = true;
            }
        } else {
            waiting_logged = false;
        }
        for (target, flags, source) in targets {
            attempt += 1;
            match timeout(Duration::from_secs(15), sender.discover(target)).await {
                Ok(Ok(Some(found))) => {
                    println!("receiver answered on attempt {attempt} via {source}: {:?} at {target}", found.computer_name);
                    chosen = Some((target, flags, found.computer_name));
                    break;
                }
                Ok(Ok(None)) => if attempt <= 3 { println!("attempt {attempt}: no receiver name"); },
                Ok(Err(e)) => if attempt <= 3 || attempt % 10 == 0 { println!("attempt {attempt}: {e}"); },
                Err(_) => if attempt <= 3 || attempt % 10 == 0 { println!("attempt {attempt}: timeout"); },
            }
        }
        if chosen.is_none() {
            tokio::select! {
                event = browse.events.recv() => match event {
                    Some(BrowseEvent::Found(found)) => {
                        if target_mac.is_none() { mdns_found = Some(found); }
                    }
                    Some(BrowseEvent::Lost(_)) | None => {}
                },
                changed = peers_rx.changed() => {
                    if changed.is_ok() {
                        let peers = peers_rx.borrow().clone();
                        let names_snapshot = names.lock().unwrap().clone();
                        emit_peer_updates(known, &peers, &names_snapshot);
                        println!("AWDL peers now: {} {:?}", peers.len(), peers);
                    }
                }
                _ = sleep(Duration::from_millis(500)) => {}
            }
        }
    }

    let (target, flags, name) = chosen.ok_or_else(|| std::io::Error::other("no AirDrop receiver answered within 120 s"))?;
    println!("sending to {name:?} at {target} flags=0x{flags:x}");
    let payload = Payload::Files(vec![path.to_path_buf()]);
    let (events_tx, mut events_rx) = tokio::sync::mpsc::unbounded_channel();
    let send_future = sender.send(target, &payload, flags, Some(events_tx));
    tokio::pin!(send_future);
    let outcome = loop {
        tokio::select! {
            event = events_rx.recv() => match event {
                Some(Event::Accepted) => println!("receiver accepted; uploading..."),
                Some(Event::Progress(p)) => if p.total > 0 { println!("progress {}/{} bytes", p.sent, p.total); },
                None => {}
            },
            result = &mut send_future => break result,
        }
    };
    match outcome {
        Ok(outcome) => println!("DONE: {outcome:?}"),
        Err(e) => return Err(format!("send failed: {e}").into()),
    }
    Ok(())
}

async fn session(channel: u8, name: String) -> Result<(), Box<dyn Error>> {
    rustls::crypto::ring::default_provider().install_default().ok();
    let (native, index, addr, mut peers_rx) = start_radio(channel).await?;
    let mut live_peers = peers_rx.clone();
    let names = Arc::new(Mutex::new(HashMap::new()));
    let mut sniffer = start_name_sniffer(names.clone());
    let mdns = Mdns::new(index);
    let mut browse = mdns.browse().await?;
    let mut sender_config = SenderConfig::new(name, sender_id(addr));
    sender_config.ask_timeout = Duration::from_secs(90);
    let sender = Sender::new(sender_config)?;
    let mut known: HashMap<String, KnownPeer> = HashMap::new();

    let (cmd_tx, mut cmd_rx) = tokio::sync::mpsc::unbounded_channel::<String>();
    std::thread::spawn(move || {
        for line in BufReader::new(std::io::stdin()).lines().flatten() {
            if cmd_tx.send(line).is_err() {
                break;
            }
        }
    });

    println!("SESSION_READY channel={channel} mac={addr:?}");
    let initial_peers = peers_rx.borrow().clone();
    let initial_names = names.lock().unwrap().clone();
    emit_peer_updates(&mut known, &initial_peers, &initial_names);
    loop {
        tokio::select! {
            changed = live_peers.changed() => {
                if changed.is_ok() {
                    let peers = live_peers.borrow().clone();
                    let names_snapshot = names.lock().unwrap().clone();
                    emit_peer_updates(&mut known, &peers, &names_snapshot);
                }
            }
            command = cmd_rx.recv() => {
                let Some(line) = command else { break };
                let mut parts = line.splitn(4, ' ');
                match parts.next().unwrap_or("") {
                    "scan" => {
                        let seconds = parts.next().and_then(|s| s.parse().ok()).unwrap_or(10);
                        let id = parts.next().unwrap_or("scan");
                        // The app clears its list when a scan starts; forget the
                        // previous snapshot so every current peer is emitted again.
                        known.clear();
                        let deadline = Instant::now() + Duration::from_secs(seconds);
                        loop {
                            tokio::select! {
                                event = browse.events.recv() => match event {
                                    Some(BrowseEvent::Found(found)) => println!(
                                        "FOUND instance={:?} host={:?} addr={} port={} flags=0x{:x}",
                                        found.instance, found.host, found.addr, found.port, found.flags
                                    ),
                                    Some(BrowseEvent::Lost(instance)) => println!("LOST instance={instance:?}"),
                                    None => break,
                                },
                                changed = peers_rx.changed() => {
                                    if changed.is_ok() {
                                        let peers = peers_rx.borrow().clone();
                                        let names_snapshot = names.lock().unwrap().clone();
                                        emit_peer_updates(&mut known, &peers, &names_snapshot);
                                        println!("AWDL peers now: {} {:?}", peers.len(), peers);
                                    }
                                },
                                _ = sleep(deadline.saturating_duration_since(Instant::now())) => break,
                            }
                        }
                        let peers = peers_rx.borrow().clone();
                        let names_snapshot = names.lock().unwrap().clone();
                        emit_peer_updates(&mut known, &peers, &names_snapshot);
                        print_peers(&peers, &names_snapshot);
                        println!("PEER_COUNT {}", peers.len());
                        println!("SCAN_DONE {id}");
                    }
                    "send" => {
                        let id = parts.next().unwrap_or("send");
                        let mac = parts.next().unwrap_or("");
                        let path = parts.next().unwrap_or("");
                        if mac.is_empty() || path.is_empty() {
                            println!("SEND_ERR {id} missing target or path");
                            continue;
                        }
                        match find_and_send(
                            &sender,
                            index,
                            &mut browse,
                            &mut peers_rx,
                            &names,
                            &mut known,
                            &PathBuf::from(path),
                            Some(mac),
                        ).await {
                            Ok(()) => println!("SEND_OK {id}"),
                            Err(e) => println!("SEND_ERR {id} {e}"),
                        }
                    }
                    "quit" => break,
                    other => println!("SESSION_ERROR unknown_command={other}"),
                }
            }
            event = browse.events.recv() => match event {
                Some(BrowseEvent::Found(found)) => println!(
                    "FOUND instance={:?} host={:?} addr={} port={} flags=0x{:x}",
                    found.instance, found.host, found.addr, found.port, found.flags
                ),
                Some(BrowseEvent::Lost(instance)) => println!("LOST instance={instance:?}"),
                None => break,
            },
            changed = peers_rx.changed() => {
                if changed.is_ok() {
                    let peers = peers_rx.borrow().clone();
                    let names_snapshot = names.lock().unwrap().clone();
                    emit_peer_updates(&mut known, &peers, &names_snapshot);
                    println!("AWDL peers now: {} {:?}", peers.len(), peers);
                }
            },
            _ = sleep(Duration::from_secs(1)) => {
                let peers = peers_rx.borrow().clone();
                let names_snapshot = names.lock().unwrap().clone();
                emit_peer_updates(&mut known, &peers, &names_snapshot);
            }
        }
    }

    if let Some(child) = sniffer.as_mut() {
        let _ = child.kill();
        let _ = child.wait();
    }
    native.stop();
    println!("SESSION_STOPPED");
    Ok(())
}

fn offer_summary(offer: &Offer) -> (usize, u64, String) {
    match offer {
        Offer::Files(files) => {
            let total = files.iter().filter_map(|file| file.size).sum();
            let names = files
                .iter()
                .take(3)
                .map(|file| file.name.clone())
                .collect::<Vec<_>>()
                .join(", ");
            (files.len(), total, names)
        }
        Offer::Urls(urls) => (
            urls.len(),
            0,
            urls.iter().take(3).cloned().collect::<Vec<_>>().join(", "),
        ),
    }
}

fn offer_total(offer: &Offer) -> u64 {
    offer_summary(offer).1
}

/// Bytes and files currently staged for an upload. Staging directories are
/// `.owly-staging-*` inside the receive destination; reading them is safe and
/// gives a usable numeric progress signal even though ReceiverEvent has none.
fn staging_usage(dir: &Path) -> (u64, u64) {
    fn walk(path: &Path, bytes: &mut u64, files: &mut u64) {
        let Ok(entries) = std::fs::read_dir(path) else { return };
        for entry in entries.flatten() {
            let Ok(kind) = entry.file_type() else { continue };
            if kind.is_dir() {
                walk(&entry.path(), bytes, files);
            } else if kind.is_file() {
                if let Ok(meta) = entry.metadata() {
                    *bytes = (*bytes).saturating_add(meta.len());
                    *files += 1;
                }
            }
        }
    }

    let mut bytes = 0u64;
    let mut files = 0u64;
    if let Ok(entries) = std::fs::read_dir(dir) {
        for entry in entries.flatten() {
            let staging = entry.file_name().to_string_lossy().starts_with(".owly-staging-");
            let is_dir = entry.file_type().map(|kind| kind.is_dir()).unwrap_or(false);
            if staging && is_dir {
                walk(&entry.path(), &mut bytes, &mut files);
            }
        }
    }
    (bytes, files)
}

async fn receive(dir: PathBuf, port: u16, channel: u8, name: String) -> Result<(), Box<dyn Error>> {
    std::fs::create_dir_all(&dir)?;
    std::fs::create_dir_all(STATE_DIR)?;
    rustls::crypto::ring::default_provider().install_default().ok();

    let (native, index, addr, _peers_rx) = start_radio(channel).await?;
    let local = addr.link_local();
    let identity = Identity::load_or_generate(Path::new(STATE_DIR))?;
    let mut config = ReceiverConfig::new(name);
    config.model_name = "Linux".into();
    config.ask_timeout = Duration::from_secs(60);
    let options = ReceiverOptions::new(config, dir.clone());
    let (receiver, mut events) = Receiver::bind(SocketAddrV6::new(local, port, 0, index), &identity, options).await?;
    let actual = receiver.local_addr()?;

    let mdns = Mdns::new(index).with_burst(Some(Burst {
        count: 64,
        interval: Duration::from_micros(16_400),
        pause: Duration::from_secs(3),
    }));
    let service = Service {
        instance: sender_id(addr),
        host: "droidy-k20".into(),
        port: actual.port(),
        addr: local,
        flags: RECEIVER_FLAGS,
    };
    let _registration = mdns.register(service).await?;
    println!("RECEIVE_READY addr={} port={} dir={}", local, actual.port(), dir.display());

    let handle = tokio::spawn(receiver.run());
    let (cmd_tx, mut cmd_rx) = tokio::sync::mpsc::unbounded_channel::<String>();
    std::thread::spawn(move || {
        for line in BufReader::new(std::io::stdin()).lines().flatten() {
            if cmd_tx.send(line).is_err() {
                break;
            }
        }
    });
    let mut requests: HashMap<u64, Request> = HashMap::new();
    let mut active: HashMap<u64, u64> = HashMap::new();
    let mut ticker = tokio::time::interval(Duration::from_millis(500));
    ticker.set_missed_tick_behavior(MissedTickBehavior::Skip);

    loop {
        tokio::select! {
            event = events.recv() => match event {
                Some(ReceiverEvent::Discovered { peer, .. }) => println!("RECEIVE_DISCOVER peer={peer}"),
                Some(ReceiverEvent::Request(request)) => {
                    let id = request.id;
                    let peer = request.peer;
                    let (files, total, names) = offer_summary(&request.ask.offer);
                    let sender = request.ask.sender_computer_name.clone();
                    println!(
                        "RECEIVE_REQUEST id={id} peer={peer} files={files} total={total} sender={sender}"
                    );
                    if !names.is_empty() {
                        println!("RECEIVE_REQUEST_FILES id={id} names={names}");
                    }
                    requests.insert(id, request);
                }
                Some(ReceiverEvent::Urls { id, urls }) => println!("RECEIVE_URLS id={id} urls={urls:?}"),
                Some(ReceiverEvent::Received { id, paths }) => {
                    requests.remove(&id);
                    active.remove(&id);
                    for path in &paths {
                        println!("RECEIVED id={id} path={}", path.display());
                    }
                    println!("RECEIVE_DONE id={id}");
                }
                Some(ReceiverEvent::Failed { id, reason }) => {
                    requests.remove(&id);
                    active.remove(&id);
                    println!("RECEIVE_FAILED id={id} reason={reason}");
                }
                Some(ReceiverEvent::Withdrawn { id }) => {
                    requests.remove(&id);
                    active.remove(&id);
                    println!("RECEIVE_WITHDRAWN id={id}");
                }
                None => break,
            },
            command = cmd_rx.recv() => {
                let Some(line) = command else { break };
                let mut parts = line.splitn(2, ' ');
                let action = parts.next().unwrap_or("");
                let id = parts.next().and_then(|value| value.trim().parse::<u64>().ok());
                match (action, id) {
                    ("accept", Some(id)) => match requests.remove(&id) {
                        Some(request) => {
                            let total = offer_total(&request.ask.offer);
                            active.insert(id, total);
                            request.accept();
                            println!("RECEIVE_ACCEPTED id={id} total={total}");
                        }
                        None => println!("RECEIVE_ERROR id={id} unknown_request"),
                    },
                    ("decline", Some(id)) => match requests.remove(&id) {
                        Some(request) => {
                            request.decline();
                            println!("RECEIVE_DECLINED id={id}");
                        }
                        None => println!("RECEIVE_ERROR id={id} unknown_request"),
                    },
                    ("quit", _) => break,
                    ("accept" | "decline", None) => {
                        println!("RECEIVE_ERROR missing_request_id");
                    }
                    (other, _) => println!("RECEIVE_ERROR unknown_command={other}"),
                }
            },
            _ = ticker.tick() => {
                if let Some((id, total)) = active.iter().next().map(|(id, total)| (*id, *total)) {
                    let (bytes, files) = staging_usage(&dir);
                    println!("RECEIVE_PROGRESS id={id} bytes={bytes} total={total} files={files}");
                }
            }
        }
    }

    for (_, request) in requests.drain() {
        request.decline();
    }
    handle.abort();
    native.stop();
    println!("RECEIVE_STOPPED");
    Ok(())
}
