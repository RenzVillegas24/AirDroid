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
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::time::Duration;

use owly_airdrop::driver::mdns::Mdns;
use owly_airdrop::driver::receiver::{
    Options as ReceiverOptions, Receiver, ReceiverEvent, Request,
};
use owly_airdrop::driver::sender::{Event, Payload, Sender};
use owly_airdrop::driver::tls::Identity;
use owly_airdrop::proto::messages::Offer;
use owly_airdrop::proto::sender::Outcome;
use owly_airdrop::proto::mdns::{
    BrowseEvent, Burst, Found, Service, RECEIVER_FLAGS, SUPPORTS_DVZIP,
};
use owly_airdrop::proto::receiver::Config as ReceiverConfig;
use owly_airdrop::proto::sender::Config as SenderConfig;
use owly_awdl::{Channel, Config, MacAddr, Presence, RadioConstraints, TxRate};
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
            let name = args.get(5).cloned().unwrap_or_else(|| "AirDroid K20".into());
            send(PathBuf::from(path), target.cloned(), channel, name).await
        }
        "session" => {
            let channel = args.get(2).and_then(|s| s.parse().ok()).unwrap_or(DEFAULT_CHANNEL);
            let name = args.get(3).cloned().unwrap_or_else(|| "AirDroid K20".into());
            let dir = PathBuf::from(
                args.get(4).cloned().unwrap_or_else(|| "/sdcard/Download/AirDroid".into()),
            );
            let port = args.get(5).and_then(|s| s.parse().ok()).unwrap_or(RECEIVER_PORT);
            session(channel, name, dir, port).await
        }
        "receive" => {
            let dir = PathBuf::from(args.get(2).ok_or("usage: helper receive <dir> <port> <channel>")?);
            let port = args.get(3).and_then(|s| s.parse().ok()).unwrap_or(RECEIVER_PORT);
            let channel = args.get(4).and_then(|s| s.parse().ok()).unwrap_or(DEFAULT_CHANNEL);
            let name = args.get(5).cloned().unwrap_or_else(|| "AirDroid K20".into());
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

/// The monitor name sniffer: a separate `owly-monitor-full` process whose
/// output is parsed for AWDL `dnm=` display names.
///
/// It is stopped during a transfer so it doesn't compete with the upload for
/// CPU, and started again afterwards. Names already learned stay in the shared
/// map, so stopping it loses nothing that was already known.
struct NameSniffer {
    names: Arc<Mutex<HashMap<String, String>>>,
    child: Option<Child>,
    paused: bool,
}

impl NameSniffer {
    fn start(names: Arc<Mutex<HashMap<String, String>>>) -> Self {
        let mut sniffer = NameSniffer {
            names,
            child: None,
            paused: false,
        };
        sniffer.spawn();
        sniffer
    }

    fn spawn(&mut self) {
        let Ok(mut child) = Command::new("/data/local/tmp/owly-app/owly-monitor-full")
            .arg(MONITOR)
            .stdout(Stdio::piped())
            .stderr(Stdio::null())
            .spawn()
        else {
            return;
        };
        let Some(stdout) = child.stdout.take() else {
            return;
        };
        let names = Arc::clone(&self.names);
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
        self.child = Some(child);
    }

    /// Stops the sniffer process while `paused`, restarts it otherwise.
    /// Idempotent, so it is safe to call after every event.
    fn set_paused(&mut self, paused: bool) {
        if self.paused == paused {
            return;
        }
        self.paused = paused;
        if paused {
            self.stop();
        } else {
            self.spawn();
        }
    }

    fn stop(&mut self) {
        if let Some(mut child) = self.child.take() {
            let _ = child.kill();
            let _ = child.wait();
        }
    }
}

impl Drop for NameSniffer {
    fn drop(&mut self) {
        self.stop();
    }
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

/// The file name shown to the user: the path's last component, or the whole
/// path when it has none.
fn display_name(path: &Path) -> String {
    path.file_name()
        .map(|name| name.to_string_lossy().into_owned())
        .unwrap_or_else(|| path.display().to_string())
}

/// Size of a file in bytes, or 0 for a directory or a path that doesn't exist.
/// A send offers one file, so this is the card's total; the upload itself is
/// compressed and reports its own total.
fn file_total(path: &Path) -> u64 {
    std::fs::metadata(path)
        .ok()
        .filter(|meta| meta.is_file())
        .map_or(0, |meta| meta.len())
}

/// The top-level items an offer names, in order. URL shares have no files.
fn offer_files(offer: &Offer) -> Vec<(String, Option<u64>)> {
    match offer {
        Offer::Files(files) => files
            .iter()
            .map(|file| (file.name.clone(), file.size))
            .collect(),
        Offer::Urls(_) => Vec::new(),
    }
}

fn outcome_reason(outcome: &Outcome) -> String {
    match outcome {
        Outcome::Delivered => "delivered".into(),
        Outcome::Declined => "declined".into(),
        Outcome::Failed(reason) => reason.clone(),
    }
}

/// Held by the send task: clears `send_active` when the task ends, including
/// on a panic, so a send can't stay "active" forever.
struct SendActive {
    active: Arc<AtomicBool>,
    id: String,
    current: Arc<Mutex<Option<String>>>,
}

impl Drop for SendActive {
    fn drop(&mut self) {
        self.active.store(false, Ordering::SeqCst);
        let mut current = self.current.lock().unwrap();
        if current.as_deref() == Some(self.id.as_str()) {
            *current = None;
        }
    }
}

/// A receive that is still uploading, for transfer priority.
struct ActiveReceive {
    total: u64,
    last_bytes: u64,
    last_progress: Instant,
}

/// The receiver driver reports nothing when a connection dies after accept but
/// before `/Upload` starts, so a receive with no staging growth for this long
/// is dropped from the active set (its own idle timeout is 60 s). Without
/// this, a vanished sender would pause discovery for the rest of the session.
const RECEIVE_STALL_TIMEOUT: Duration = Duration::from_secs(120);

/// The receiver can stop answering new `/Discover` connections right after a
/// transfer even while its AWDL peer stays visible. Ten-second attempts make
/// the next request look 20+ seconds late; retry the small discover exchange
/// sooner instead.
const SEND_DISCOVER_ATTEMPT_TIMEOUT: Duration = Duration::from_secs(4);

/// Pauses or resumes background discovery for a transfer. Prints only on a
/// change, so the UI can show "discovery paused" without log spam.
fn update_transfer_pause(sniffer: &mut NameSniffer, paused: &mut bool, now: bool) {
    if *paused == now {
        return;
    }
    *paused = now;
    sniffer.set_paused(now);
    println!("DISCOVERY_PAUSED paused={now}");
}

/// ACK/retry experiment from the performance plan (Phase E).
///
/// `Presence::Fixed` defaults `no_ack` to `true`, so every unicast data frame
/// is sent once and a lost one becomes a TCP retransmission. The hidden STA
/// vdev behind this monitor can do normal ACK/retry, so the experiment turns
/// `no_ack` off. `OWLY_TX_NO_ACK=1` (or `true`/`on`) and `OWLY_TX_NO_ACK=0`
/// (or `false`/`off`) override it for an A/B measurement without a rebuild.
///
/// This is the only radio change in the experiment: rate, A-MSDU, Block Ack
/// and the rest of `Config` keep their defaults.
const TX_NO_ACK_DEFAULT: bool = false;

fn tx_no_ack() -> bool {
    match env::var("OWLY_TX_NO_ACK") {
        Ok(value) => match value.trim() {
            "1" | "true" | "yes" | "on" => true,
            "0" | "false" | "no" | "off" => false,
            _ => TX_NO_ACK_DEFAULT,
        },
        Err(_) => TX_NO_ACK_DEFAULT,
    }
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
    let mut cfg = Config::new(addr, "airdroid-k20", radio);
    cfg.rssi_threshold = Some(-85);
    cfg.max_frame = 2304;
    // The iPhone advertises HT rates and A-MSDU support. The legacy 12 Mbit/s
    // BASIC rate plus one frame per TCP segment is what produced the 512 KiB
    // then stall pattern; use the one-stream MCS 7 rate and aggregate where
    // the peer accepts it.
    cfg.rate = TxRate::Ht { mcs: 7, sgi: true };
    cfg.amsdu = true;
    // The ACK/retry experiment, see `TX_NO_ACK_DEFAULT`.
    cfg.no_ack = tx_no_ack();
    println!(
        "RADIO_CONFIG no_ack={} rate={} amsdu={}",
        cfg.no_ack, cfg.rate, cfg.amsdu
    );
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
    let mut sniffer = NameSniffer::start(names.clone());
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
    sniffer.stop();
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
    let mut sniffer = NameSniffer::start(names.clone());
    let mdns = Mdns::new(index);
    let mut browse = mdns.browse().await?;
    let mut sender_config = SenderConfig::new(name, sender_id(addr));
    sender_config.ask_timeout = Duration::from_secs(90);
    let sender = Sender::new(sender_config)?;
    let mdns_cache = Arc::new(Mutex::new(MdnsCache::default()));
    // One file per send, so the send ID is fixed for this command.
    //
    // New fields go before `id=` so the existing `total=`-last and
    // `bytes=`-then-`total=` parsing in the app keeps working unchanged.
    let source_total = file_total(&path);
    println!(
        "SEND_FILE_START source_total={source_total} id=send name={} total={source_total}",
        display_name(&path)
    );
    let (_cancel_tx, mut cancel_rx) = watch::channel(false);
    let result = find_and_send(
        &sender,
        index,
        Some(&mut browse),
        &mut peers_rx,
        &names,
        &path,
        source_total,
        target.as_deref(),
        &mdns_cache,
        Some("send"),
        &mut cancel_rx,
    )
    .await;
    match &result {
        Ok(Outcome::Delivered) => println!(
            "SEND_FILE_DONE source_total={source_total} id=send path={}",
            path.display()
        ),
        Ok(outcome) => {
            println!("SEND_FILE_ERR source_total={source_total} id=send reason={outcome:?}")
        }
        Err(e) => println!("SEND_FILE_ERR source_total={source_total} id=send reason={e}"),
    }
    sniffer.stop();
    native.stop();
    result.map(|_| ())
}

/// The AirDrop mDNS services seen by the session browse, keyed by AWDL MAC.
/// The app uses the same FOUND/LOST events for its peer-readiness state; the
/// send task uses this cache to avoid a redundant /Discover.
#[derive(Default)]
struct MdnsCache {
    by_mac: HashMap<String, Found>,
    by_instance: HashMap<String, String>,
}

impl MdnsCache {
    fn found(&mut self, found: &Found) {
        let Some(mac) = MacAddr::from_link_local(found.addr) else {
            return;
        };
        let mac = mac.to_string();
        self.by_mac.insert(mac.clone(), found.clone());
        self.by_instance.insert(found.instance.clone(), mac);
    }

    fn lost(&mut self, instance: &str) {
        if let Some(mac) = self.by_instance.remove(instance) {
            self.by_mac.remove(&mac);
        }
    }

    fn target(&self, mac: &str) -> Option<Found> {
        self.by_mac
            .iter()
            .find(|(key, _)| key.eq_ignore_ascii_case(mac))
            .map(|(_, found)| found.clone())
    }

    fn clear(&mut self) {
        self.by_mac.clear();
        self.by_instance.clear();
    }
}

/// Does this mDNS receiver resolve to the selected AWDL peer?
fn mdns_matches_target(found: &Found, target_mac: Option<&str>) -> bool {
    let Some(target) = target_mac else {
        return true;
    };
    MacAddr::from_link_local(found.addr)
        .map(|mac| mac.to_string().eq_ignore_ascii_case(target))
        .unwrap_or(false)
}

/// A fresh /Ask connection can be refused or time out while the iPhone's
/// AirDrop port is still waking up. Retry after a short /Discover probe instead
/// of reporting a send failure with no prompt.
fn retryable_connect(e: &std::io::Error) -> bool {
    matches!(
        e.kind(),
        std::io::ErrorKind::TimedOut
            | std::io::ErrorKind::ConnectionRefused
            | std::io::ErrorKind::ConnectionReset
            | std::io::ErrorKind::ConnectionAborted
            | std::io::ErrorKind::NotConnected
    )
}

async fn find_and_send(
    sender: &Sender,
    index: u32,
    browse: Option<&mut owly_airdrop::driver::mdns::Browse>,
    peers_rx: &mut watch::Receiver<Vec<PeerInfo>>,
    names: &Arc<Mutex<HashMap<String, String>>>,
    path: &Path,
    source_total: u64,
    target_mac: Option<&str>,
    mdns_cache: &Arc<Mutex<MdnsCache>>,
    report_id: Option<&str>,
    cancel: &mut watch::Receiver<bool>,
) -> Result<Outcome, Box<dyn Error>> {
    let mut browse = browse;
    let deadline = Instant::now() + Duration::from_secs(120);
    let mut mdns_found: Option<Found> = None;
    let mut attempt = 0u32;
    let mut chosen: Option<(SocketAddrV6, u32, String)> = None;
    let mut waiting_logged = false;

    while Instant::now() < deadline && chosen.is_none() {
        if *cancel.borrow() {
            return Ok(Outcome::Failed("cancelled".into()));
        }
        // Standalone `send` owns a browse. The session send job does not:
        // the session loop feeds `mdns_cache` while this job waits.
        if let Some(browse) = browse.as_deref_mut() {
            loop {
                match browse.events.try_recv() {
                    Ok(BrowseEvent::Found(found)) => {
                        println!(
                            "mDNS FOUND instance={:?} host={:?} addr={} port={} flags=0x{:x}",
                            found.instance, found.host, found.addr, found.port, found.flags
                        );
                        if mdns_matches_target(&found, target_mac) {
                            mdns_found = Some(found);
                        }
                    }
                    Ok(BrowseEvent::Lost(instance)) => {
                        println!("mDNS LOST instance={instance:?}");
                        mdns_found = None;
                    }
                    Err(tokio::sync::mpsc::error::TryRecvError::Empty) => break,
                    Err(tokio::sync::mpsc::error::TryRecvError::Disconnected) => break,
                }
            }
        }
        let peers = peers_rx.borrow().clone();
        let names_snapshot = names.lock().unwrap().clone();
        // A cached mDNS service is only an address. Every attempt still has
        // to pass /Discover before /Ask; that is the receiver-port gate.
        if mdns_found.is_none() {
            if let Some(target) = target_mac {
                mdns_found = mdns_cache.lock().unwrap().target(target);
            }
        }
        let mut targets: Vec<(SocketAddrV6, u32, &str)> = Vec::new();
        if let Some(found) = &mdns_found {
            targets.push((
                SocketAddrV6::new(found.addr, found.port, 0, index),
                found.flags,
                "mDNS",
            ));
        }
        for peer in &peers {
            let mac = peer.mac.to_string();
            if let Some(target) = target_mac {
                if !mac.eq_ignore_ascii_case(target) {
                    continue;
                }
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
            let discover = timeout(SEND_DISCOVER_ATTEMPT_TIMEOUT, sender.discover(target));
            let discovered = tokio::select! {
                _ = cancel.changed() => {
                    if *cancel.borrow() {
                        return Ok(Outcome::Failed("cancelled".into()));
                    }
                    continue;
                }
                result = discover => result,
            };
            match discovered {
                Ok(Ok(Some(found))) => {
                    println!(
                        "receiver answered on attempt {attempt} via {source}: {:?} at {target}",
                        found.computer_name
                    );
                    chosen = Some((target, flags, found.computer_name));
                    break;
                }
                Ok(Ok(None)) => if attempt <= 3 { println!("attempt {attempt}: no receiver name"); },
                Ok(Err(e)) => if attempt <= 3 || attempt % 10 == 0 { println!("attempt {attempt}: {e}"); },
                Err(_) => if attempt <= 3 || attempt % 10 == 0 { println!("attempt {attempt}: timeout"); },
            }
        }
        if chosen.is_none() {
            let browse_event = async {
                match browse.as_deref_mut() {
                    Some(browse) => browse.events.recv().await,
                    None => std::future::pending().await,
                }
            };
            tokio::select! {
                event = browse_event => match event {
                    Some(BrowseEvent::Found(found)) => {
                        if mdns_matches_target(&found, target_mac) {
                            mdns_found = Some(found);
                        }
                    }
                    Some(BrowseEvent::Lost(_)) | None => {}
                },
                changed = peers_rx.changed() => {
                    if changed.is_ok() {
                        let peers = peers_rx.borrow().clone();
                        println!("AWDL peers now: {} {:?}", peers.len(), peers);
                    }
                }
                _ = cancel.changed() => {
                    if *cancel.borrow() {
                        return Ok(Outcome::Failed("cancelled".into()));
                    }
                }
                _ = sleep(Duration::from_millis(500)) => {}
            }
        }
    }

    let (target, flags, name) = chosen.ok_or_else(|| std::io::Error::other("no AirDrop receiver answered within 120 s"))?;
    println!("sending to {name:?} at {target} flags=0x{flags:x}");
    let payload = Payload::Files(vec![path.to_path_buf()]);
    let mut connect_retries = 2u32;
    let outcome = loop {
        if *cancel.borrow() {
            break Ok(Outcome::Failed("cancelled".into()));
        }
        let (events_tx, mut events_rx) = tokio::sync::mpsc::unbounded_channel();
        // Cancellation is owned by the sender so it can close the TLS/TCP
        // connection abortively. Dropping this future from the helper would
        // leave the peer reading until its idle timeout.
        let send_future = sender.send_with_cancel(
            target,
            &payload,
            flags,
            Some(events_tx),
            cancel.clone(),
        );
        tokio::pin!(send_future);
        // Transfer priority: once the receiver is chosen, only the upload and its
        // progress are handled. Peer updates and mDNS browse events would compete
        // with it, so they are left for after the transfer (the session loop does
        // the same for its own discovery).
        let mut last_report: Option<Instant> = None;
        let attempt = loop {
            tokio::select! {
                event = events_rx.recv() => match event {
                    Some(Event::Accepted) => println!("receiver accepted; uploading..."),
                    Some(Event::Progress(p)) => {
                        if p.total > 0 {
                            println!("progress {}/{} bytes", p.sent, p.total);
                            if let Some(id) = report_id {
                                // One line per 64 KiB chunk would add stdout work
                                // to a fast upload, so the per-file line is capped
                                // at about ten a second (plus the final one).
                                let due = last_report
                                    .map_or(true, |last| last.elapsed() >= Duration::from_millis(100));
                                if due || p.sent >= p.total {
                                    last_report = Some(Instant::now());
                                    // `total` is the compressed `/Upload` body, not
                                    // the source file's size: the sender archives
                                    // and compresses the file before the offer.
                                    // `source_total` keeps the uncompressed size
                                    // alongside it, so the app can show both
                                    // without guessing.
                                    println!(
                                        "SEND_FILE_PROGRESS source_total={source_total} upload_total={} id={id} bytes={} total={}",
                                        p.total, p.sent, p.total
                                    );
                                }
                            }
                        }
                    }
                    None => {}
                },
                result = &mut send_future => break result,
            }
        };
        match attempt {
            Ok(Outcome::Failed(reason)) if reason == "cancelled" => {
                break Ok(Outcome::Failed(reason));
            }
            Err(e) if connect_retries > 0 && retryable_connect(&e) => {
                connect_retries -= 1;
                println!("send connect failed: {e}; retrying /Ask...");
                // Do not probe /Discover again: it just answered, and another
                // probe can consume the receiver port the /Ask needs.
                sleep(Duration::from_millis(700)).await;
                continue;
            }
            other => break other,
        }
    };
    match outcome {
        Ok(outcome) => {
            println!("DONE: {outcome:?}");
            Ok(outcome)
        }
        Err(e) => Err(format!("send failed: {e}").into()),
    }
}

async fn session(
    channel: u8,
    name: String,
    receive_dir: PathBuf,
    receive_port: u16,
) -> Result<(), Box<dyn Error>> {
    rustls::crypto::ring::default_provider().install_default().ok();
    std::fs::create_dir_all(&receive_dir)?;
    std::fs::create_dir_all(STATE_DIR)?;
    let (native, index, addr, mut peers_rx) = start_radio(channel).await?;
    let names = Arc::new(Mutex::new(HashMap::new()));
    let mut sniffer = NameSniffer::start(names.clone());
    let mdns = Mdns::new(index).with_burst(Some(Burst {
        count: 64,
        interval: Duration::from_micros(16_400),
        pause: Duration::from_secs(3),
    }));
    let identity = Identity::load_or_generate(Path::new(STATE_DIR))?;
    let mut receiver_config = ReceiverConfig::new(name.clone());
    receiver_config.model_name = "Linux".into();
    receiver_config.ask_timeout = Duration::from_secs(60);
    let receiver_options = ReceiverOptions::new(receiver_config, receive_dir.clone());
    let local = addr.link_local();
    let (receiver, mut receiver_events) = Receiver::bind(
        SocketAddrV6::new(local, receive_port, 0, index),
        &identity,
        receiver_options,
    )
    .await?;
    let actual_port = receiver.local_addr()?.port();
    let receiver_control = receiver.control();
    let service = Service {
        instance: sender_id(addr),
        host: "airdroid-k20".into(),
        port: actual_port,
        addr: local,
        flags: RECEIVER_FLAGS,
    };
    let mut registration = Some(mdns.register(service.clone()).await?);
    let _receiver_task = tokio::spawn(receiver.run());
    let mut browse = mdns.browse().await?;
    let mut sender_config = SenderConfig::new(name, sender_id(addr));
    sender_config.ask_timeout = Duration::from_secs(90);
    let sender = Arc::new(Sender::new(sender_config)?);
    let mdns_cache = Arc::new(Mutex::new(MdnsCache::default()));
    let send_active = Arc::new(AtomicBool::new(false));
    let active_send: Arc<Mutex<Option<String>>> = Arc::new(Mutex::new(None));
    let (send_cancel_tx, send_cancel_rx) = watch::channel(false);
    let mut known: HashMap<String, KnownPeer> = HashMap::new();
    let mut requests: HashMap<u64, Request> = HashMap::new();
    // Accepted uploads that are still running. A non-empty map means a
    // receive is active for transfer priority.
    let mut active_receives: HashMap<u64, ActiveReceive> = HashMap::new();
    let mut pause_discovery_on_transfer = true;
    let mut visible_during_transfer = true;
    let mut transfer_paused = false;
    let mut progress_ticker = tokio::time::interval(Duration::from_millis(500));
    progress_ticker.set_missed_tick_behavior(MissedTickBehavior::Skip);
    let mut peer_ticker = tokio::time::interval(Duration::from_secs(1));
    peer_ticker.set_missed_tick_behavior(MissedTickBehavior::Skip);

    let (cmd_tx, mut cmd_rx) = tokio::sync::mpsc::unbounded_channel::<String>();
    std::thread::spawn(move || {
        for line in BufReader::new(std::io::stdin()).lines().flatten() {
            if cmd_tx.send(line).is_err() {
                break;
            }
        }
    });

    println!("SESSION_READY channel={channel} mac={addr:?}");
    println!(
        "RECEIVE_READY addr={} port={} dir={}",
        local,
        actual_port,
        receive_dir.display()
    );
    let initial_peers = peers_rx.borrow().clone();
    let initial_names = names.lock().unwrap().clone();
    emit_peer_updates(&mut known, &initial_peers, &initial_names);
    loop {
        tokio::select! {
            // Transfer priority: peer updates are non-urgent, so they are
            // dropped while a transfer runs. The watch keeps the latest
            // snapshot, and the 1 s ticker emits it once the transfer ends.
            changed = peers_rx.changed() => {
                if changed.is_ok() && !transfer_paused {
                    let peers = peers_rx.borrow().clone();
                    let names_snapshot = names.lock().unwrap().clone();
                    emit_peer_updates(&mut known, &peers, &names_snapshot);
                    println!("AWDL peers now: {} {:?}", peers.len(), peers);
                }
            }
            // Receiver events stay high priority, including `/Ask` requests,
            // so accept/decline keeps working while a send is active.
            receiver_event = receiver_events.recv() => match receiver_event {
                Some(ReceiverEvent::Discovered { peer, .. }) => {
                    println!("RECEIVE_DISCOVER peer={peer}");
                }
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
                Some(ReceiverEvent::Urls { id, urls }) => {
                    println!("RECEIVE_URLS id={id} urls={urls:?}");
                }
                Some(ReceiverEvent::Progress { id, path, bytes }) => {
                    if let Some(state) = active_receives.get_mut(&id) {
                        state.last_progress = Instant::now();
                    }
                    println!(
                        "RECEIVE_FILE_PROGRESS id={id} name={} bytes={bytes} total=0",
                        display_name(&path)
                    );
                }
                Some(ReceiverEvent::Received { id, paths }) => {
                    requests.remove(&id);
                    active_receives.remove(&id);
                    // `paths` is in the receiver's staging top-level order,
                    // which is the order the upload archive listed the offered
                    // items. That makes the position a stable index even when
                    // a collision renames the final path.
                    for (index, path) in paths.iter().enumerate() {
                        println!("RECEIVED id={id} path={}", path.display());
                        println!(
                            "RECEIVE_FILE_DONE index={index} id={id} name={} path={}",
                            display_name(path),
                            path.display()
                        );
                    }
                    println!("RECEIVE_DONE id={id}");
                }
                Some(ReceiverEvent::Failed { id, reason }) => {
                    requests.remove(&id);
                    active_receives.remove(&id);
                    println!("RECEIVE_FAILED id={id} reason={reason}");
                }
                Some(ReceiverEvent::Withdrawn { id }) => {
                    requests.remove(&id);
                    active_receives.remove(&id);
                    println!("RECEIVE_WITHDRAWN id={id}");
                }
                Some(ReceiverEvent::Aborted { id }) => {
                    requests.remove(&id);
                    active_receives.remove(&id);
                    println!("RECEIVE_ABORTED id={id}");
                }
                None => {}
            },
            command = cmd_rx.recv() => {
                let Some(line) = command else { break };
                let mut parts = line.splitn(4, ' ');
                match parts.next().unwrap_or("") {
                    "set-transfer-policy" => {
                        for arg in line.split_whitespace().skip(1) {
                            if let Some(value) = arg.strip_prefix("pause_discovery=") {
                                pause_discovery_on_transfer = value != "0";
                            } else if let Some(value) = arg.strip_prefix("visible_during_transfer=") {
                                visible_during_transfer = value != "0";
                            }
                        }
                        println!(
                            "TRANSFER_POLICY pause_discovery={pause_discovery_on_transfer} visible_during_transfer={visible_during_transfer}"
                        );
                    }
                    "scan" => {
                        let seconds = parts.next().and_then(|s| s.parse().ok()).unwrap_or(10);
                        let id = parts.next().unwrap_or("scan");
                        // The app clears its list when a scan starts; forget the
                        // previous snapshot so every current peer is emitted again.
                        known.clear();
                        mdns_cache.lock().unwrap().clear();
                        let deadline = Instant::now() + Duration::from_secs(seconds);
                        loop {
                            tokio::select! {
                                event = browse.events.recv() => match event {
                                    Some(BrowseEvent::Found(found)) => {
                                        mdns_cache.lock().unwrap().found(&found);
                                        println!(
                                            "FOUND instance={:?} host={:?} addr={} port={} flags=0x{:x}",
                                            found.instance, found.host, found.addr, found.port, found.flags
                                        );
                                    }
                                    Some(BrowseEvent::Lost(instance)) => {
                                        mdns_cache.lock().unwrap().lost(&instance);
                                        println!("LOST instance={instance:?}");
                                    }
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
                        let id = parts.next().unwrap_or("send").to_string();
                        let mac = parts.next().unwrap_or("").to_string();
                        let path = parts.next().unwrap_or("").to_string();
                        if mac.is_empty() || path.is_empty() {
                            println!("SEND_ERR {id} missing target or path");
                            continue;
                        }
                        if send_active.swap(true, Ordering::SeqCst) {
                            println!("SEND_ERR {id} send already active");
                            continue;
                        }
                        // Resets `send_active` when the task ends, including on
                        // a panic, so one stuck task can't block sends for good.
                        let _ = send_cancel_tx.send(false);
                        *active_send.lock().unwrap() = Some(id.clone());
                        let send_slot = SendActive {
                            active: Arc::clone(&send_active),
                            id: id.clone(),
                            current: Arc::clone(&active_send),
                        };
                        // One file per send task, so this card is the whole
                        // transfer. The file size is the uncompressed size;
                        // progress totals below are the compressed upload body.
                        // New fields go before `id=` so the app's existing
                        // `total=`-last parsing keeps working.
                        let path = PathBuf::from(path);
                        let source_total = file_total(&path);
                        println!(
                            "SEND_FILE_START source_total={source_total} id={id} name={} total={source_total}",
                            display_name(&path)
                        );
                        let sender = Arc::clone(&sender);
                        let mut send_peers = peers_rx.clone();
                        let send_names = Arc::clone(&names);
                        let send_mdns_cache = Arc::clone(&mdns_cache);
                        let mut send_cancel_rx = send_cancel_rx.clone();
                        tokio::spawn(async move {
                            let _send_slot = send_slot;
                            let result = find_and_send(
                                sender.as_ref(),
                                index,
                                None,
                                &mut send_peers,
                                &send_names,
                                &path,
                                source_total,
                                Some(&mac),
                                &send_mdns_cache,
                                Some(&id),
                                &mut send_cancel_rx,
                            ).await;
                            match result {
                                Ok(Outcome::Delivered) => {
                                    println!(
                                        "SEND_FILE_DONE source_total={source_total} id={id} path={}",
                                        path.display()
                                    );
                                    println!("SEND_OK {id}");
                                }
                                Ok(Outcome::Failed(reason)) if reason == "cancelled" => {
                                    println!(
                                        "SEND_FILE_CANCELLED source_total={source_total} id={id}"
                                    );
                                    println!("SEND_CANCELLED {id}");
                                }
                                Ok(outcome) => {
                                    let reason = outcome_reason(&outcome);
                                    println!(
                                        "SEND_FILE_ERR source_total={source_total} id={id} reason={reason}"
                                    );
                                    println!("SEND_ERR {id} {reason}");
                                }
                                Err(e) => {
                                    println!(
                                        "SEND_FILE_ERR source_total={source_total} id={id} reason={e}"
                                    );
                                    println!("SEND_ERR {id} {e}");
                                }
                            }
                        });
                    }
                    "accept" => {
                        let id = parts.next().and_then(|s| s.trim().parse::<u64>().ok());
                        match id.and_then(|id| requests.remove(&id).map(|request| (id, request))) {
                            Some((id, request)) => {
                                let total = offer_total(&request.ask.offer);
                                let files = offer_files(&request.ask.offer);
                                request.accept();
                                // The receiver API has no per-file progress, so
                                // start every offered file now and report only
                                // aggregate bytes until each file lands. URL
                                // shares have no files and no upload to track.
                                if !files.is_empty() {
                                    active_receives.insert(
                                        id,
                                        ActiveReceive {
                                            total,
                                            last_bytes: 0,
                                            last_progress: Instant::now(),
                                        },
                                    );
                                }
                                println!("RECEIVE_ACCEPTED id={id} total={total}");
                                for (index, (name, size)) in files.into_iter().enumerate() {
                                    println!(
                                        "RECEIVE_FILE_START index={index} id={id} name={name} total={}",
                                        size.unwrap_or(0)
                                    );
                                }
                            }
                            None => println!("RECEIVE_ERROR unknown_request"),
                        }
                    }
                    "decline" => {
                        let id = parts.next().and_then(|s| s.trim().parse::<u64>().ok());
                        match id.and_then(|id| requests.remove(&id).map(|request| (id, request))) {
                            Some((id, request)) => {
                                request.decline();
                                println!("RECEIVE_DECLINED id={id}");
                            }
                            None => println!("RECEIVE_ERROR unknown_request"),
                        }
                    }
                    "abort-receive" => {
                        let id = parts.next().and_then(|s| s.trim().parse::<u64>().ok());
                        match id {
                            Some(id) if receiver_control.abort_request(id) => {
                                // The receiver emits Aborted only after the
                                // connection has been closed abortively.
                                println!("RECEIVE_ABORTING id={id}");
                            }
                            Some(id) => println!("RECEIVE_ABORT_ERR id={id} not active"),
                            None => println!("RECEIVE_ABORT_ERR missing id"),
                        }
                    }
                    "cancel" => {
                        let id = parts.next().unwrap_or("").trim().to_string();
                        let current = active_send.lock().unwrap().clone();
                        if current.as_deref() == Some(id.as_str()) {
                            let _ = send_cancel_tx.send(true);
                            println!("SEND_CANCEL {id}");
                        } else {
                            println!("SEND_ERR {id} not active");
                        }
                    }
                    "quit" => break,
                    other => println!("SESSION_ERROR unknown_command={other}"),
                }
            }
            // AirDrop readiness still has to reach the app during a transfer:
            // turning AirDrop off mid-send must remove the peer instead of
            // leaving a stale card. Peer updates remain paused separately.
            event = browse.events.recv() => match event {
                None => break,
                Some(BrowseEvent::Found(found)) => {
                    mdns_cache.lock().unwrap().found(&found);
                    println!(
                        "FOUND instance={:?} host={:?} addr={} port={} flags=0x{:x}",
                        found.instance, found.host, found.addr, found.port, found.flags
                    );
                }
                Some(BrowseEvent::Lost(instance)) => {
                    mdns_cache.lock().unwrap().lost(&instance);
                    println!("LOST instance={instance:?}");
                }
            },
            // Aggregate receive progress. The receiver library reports only
            // `Received { paths }` at completion, so there is no per-file
            // progress event to forward. Staging directories are randomly
            // named and carry no request id, so with more than one accepted
            // upload they cannot be attributed to a transfer; their layout can
            // also differ from the offered names (wrapped files are flattened,
            // sidecars are skipped). Emitting per-file progress from that would
            // be a guess, so this stays aggregate and per-file start/done above
            // are the only per-file signals.
            _ = progress_ticker.tick(), if !active_receives.is_empty() => {
                let (bytes, files) = staging_usage(&receive_dir);
                let now = Instant::now();
                let mut stalled = Vec::new();
                for (id, state) in active_receives.iter_mut() {
                    if bytes != state.last_bytes {
                        state.last_bytes = bytes;
                        state.last_progress = now;
                    } else if state.last_progress.elapsed() >= RECEIVE_STALL_TIMEOUT {
                        stalled.push(*id);
                    }
                }
                for id in stalled {
                    active_receives.remove(&id);
                    println!("RECEIVE_FAILED id={id} reason=no upload progress");
                }
                if let Some((id, state)) = active_receives.iter().next() {
                    println!(
                        "RECEIVE_PROGRESS id={id} bytes={bytes} total={} files={files}",
                        state.total
                    );
                }
            },
            _ = peer_ticker.tick() => {
                if !transfer_paused {
                    let peers = peers_rx.borrow().clone();
                    let names_snapshot = names.lock().unwrap().clone();
                    emit_peer_updates(&mut known, &peers, &names_snapshot);
                }
            }
        }
        // The transfer state is send-task activity plus accepted uploads.
        // Changing it stops or restarts the name sniffer and is announced so
        // the UI can show that discovery is paused.
        let transfer_active = send_active.load(Ordering::SeqCst) || !active_receives.is_empty();
        let paused_now = pause_discovery_on_transfer && transfer_active;
        update_transfer_pause(&mut sniffer, &mut transfer_paused, paused_now);
        let should_advertise = visible_during_transfer || !transfer_active;
        if should_advertise && registration.is_none() {
            registration = Some(mdns.register(service.clone()).await?);
            println!("VISIBILITY visible=true");
        } else if !should_advertise && registration.is_some() {
            registration = None;
            println!("VISIBILITY visible=false");
        }
    }

    sniffer.stop();
    native.stop();
    println!("SESSION_STOPPED");
    Ok(())
}

fn offer_summary(offer: &Offer) -> (usize, u64, String) {
    let total = offer_total(offer);
    match offer {
        Offer::Files(files) => {
            // Every offered top-level name, in offer order. The app caps how
            // many it displays; the helper must not hide files from it.
            let names = files
                .iter()
                .map(|file| file.name.clone())
                .collect::<Vec<_>>()
                .join(", ");
            (files.len(), total, names)
        }
        Offer::Urls(urls) => (
            urls.len(),
            total,
            urls.iter().cloned().collect::<Vec<_>>().join(", "),
        ),
    }
}

fn offer_total(offer: &Offer) -> u64 {
    match offer {
        Offer::Files(files) => files.iter().filter_map(|file| file.size).sum(),
        Offer::Urls(_) => 0,
    }
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
        host: "airdroid-k20".into(),
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
                Some(ReceiverEvent::Progress { id, path, bytes }) => {
                    println!(
                        "RECEIVE_FILE_PROGRESS id={id} name={} bytes={bytes} total=0",
                        display_name(&path)
                    );
                }
                Some(ReceiverEvent::Received { id, paths }) => {
                    requests.remove(&id);
                    active.remove(&id);
                    // Same order as `RECEIVE_FILE_START`: the receiver returns
                    // staging top-level paths in upload archive order.
                    for (index, path) in paths.iter().enumerate() {
                        println!("RECEIVED id={id} path={}", path.display());
                        println!(
                            "RECEIVE_FILE_DONE index={index} id={id} name={} path={}",
                            display_name(path),
                            path.display()
                        );
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
                Some(ReceiverEvent::Aborted { id }) => {
                    requests.remove(&id);
                    active.remove(&id);
                    println!("RECEIVE_ABORTED id={id}");
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
                            let files = offer_files(&request.ask.offer);
                            if !files.is_empty() {
                                active.insert(id, total);
                            }
                            request.accept();
                            println!("RECEIVE_ACCEPTED id={id} total={total}");
                            for (index, (name, size)) in files.into_iter().enumerate() {
                                println!(
                                    "RECEIVE_FILE_START index={index} id={id} name={name} total={}",
                                    size.unwrap_or(0)
                                );
                            }
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
