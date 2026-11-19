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
use std::process::Command;
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

/// The SRV host label for `name`, DNS-safe: lowercased letters, digits and
/// single hyphens, at most 63 bytes. Falls back to the service ID so the host
/// is always derived, never a fixed string.
fn host_label(name: &str, instance: &str) -> String {
    let mut label: String = name
        .chars()
        .map(|c| {
            if c.is_ascii_alphanumeric() {
                c.to_ascii_lowercase()
            } else {
                '-'
            }
        })
        .collect();
    while label.contains("--") {
        label = label.replace("--", "-");
    }
    let label = label.trim_matches('-');
    if label.is_empty() {
        format!("owly-{instance}")
    } else {
        label.chars().take(63).collect()
    }
}

/// Keeps the AirDrop name a receiver announces (`dnm=` in its mDNS TXT), keyed
/// by its AWDL address. That name is what the AirDrop sheet shows; iOS puts a
/// UUID in the SRV host name, so the host name must not be used.
fn remember_service_name(names: &Arc<Mutex<HashMap<String, String>>>, found: &Found) {
    if found.name.is_empty() {
        return;
    }
    let Some(mac) = MacAddr::from_link_local(found.addr) else {
        return;
    };
    let key = mac.to_string();
    let mut map = names.lock().unwrap();
    if map.get(&key) != Some(&found.name) {
        println!("AWDL name: {key} = {}", found.name);
        map.insert(key, found.name.clone());
    }
}

fn print_peers(peers: &[PeerInfo], names: &HashMap<String, String>) {
    for peer in peers {
        let mac = peer.mac.to_string();
        let name = names.get(&mac).cloned().unwrap_or_else(|| peer.name.clone());
        println!(
            "PEER mac={mac} name={} devclass={} rssi={} channel={}",
            name,
            peer.devclass,
            peer.rssi.unwrap_or(0),
            peer.dominant_channel
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
            if known.get(&mac).map_or(true, |old| old.name != name) {
                println!("PEER_NAME mac={mac} name={name}");
            }
            println!(
                "PEER mac={mac} name={name} devclass={} rssi={} channel={}",
                peer.devclass,
                peer.rssi.unwrap_or(0),
                peer.dominant_channel
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
const SEND_DISCOVER_ATTEMPT_TIMEOUT: Duration = Duration::from_secs(2);

/// Pauses or resumes background discovery for a transfer. Prints only on a
/// change, so the UI can show "discovery paused" without log spam.
fn update_transfer_pause(paused: &mut bool, now: bool) {
    if *paused == now {
        return;
    }
    *paused = now;
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

/// Tunes the monitor interface to `channel`.
///
/// The channel is the difference between hearing nothing and hearing the
/// iPhone: a monitor vdev keeps whatever channel the driver came up on. Tune
/// through nl80211 in the helper itself, so a fresh install needs nothing
/// outside the APK.
fn set_radio_channel(channel: u8) {
    let Some(freq) = channel_freq(channel) else {
        println!("RADIO_CHANNEL channel={channel} unsupported");
        return;
    };
    match set_channel_native(freq) {
        Ok(()) => println!("RADIO_CHANNEL channel={channel} freq={freq} via=nl80211"),
        Err(e) => println!("RADIO_CHANNEL channel={channel} SET_FAILED {e}"),
    }
}

fn set_channel_native(freq: &str) -> Result<(), Box<dyn Error>> {
    let index: u32 = std::fs::read_to_string(format!("/sys/class/net/{MONITOR}/ifindex"))?
        .trim()
        .parse()?;
    let freq: u32 = freq.parse()?;
    owly_radio::nl80211::Nl80211::open()?.set_freq(index, freq)?;
    Ok(())
}

/// Widen the kernel TCP windows for the AWDL transfer. Android's Wi-Fi HAL
/// caps tcp_limit_output_bytes at 256 KiB and auto-tunes the receive window
/// conservatively; both are small for a time-slotted link. These values are
/// intentionally an experiment and are restored by rebooting the phone.
fn tune_tcp_for_awdl() {
    let settings = [
        ("/proc/sys/net/core/rmem_max", "16777216"),
        ("/proc/sys/net/core/wmem_max", "16777216"),
        ("/proc/sys/net/ipv4/tcp_rmem", "262144 4194304 16777216"),
        ("/proc/sys/net/ipv4/tcp_wmem", "262144 4194304 16777216"),
        ("/proc/sys/net/ipv4/tcp_limit_output_bytes", "4194304"),
        ("/proc/sys/net/ipv4/tcp_slow_start_after_idle", "0"),
    ];
    for (path, value) in settings {
        if let Err(e) = std::fs::write(path, format!("{value}\n")) {
            println!("TCP_TUNE {path} failed: {e}");
        }
    }
    println!("TCP_TUNE rmem_max=16777216 tcp_rmem=4MiB wmem=4MiB limit_output=4MiB slow_start_after_idle=0");
}

async fn start_radio(channel: u8) -> Result<(Native, u32, MacAddr, watch::Receiver<Vec<PeerInfo>>), Box<dyn Error>> {
    if !std::path::Path::new("/dev/net/tun").exists()
        && !std::path::Path::new("/dev/tun").exists()
    {
        return Err("no TUN device; awdl0 cannot be created".into());
    }
    let channel = if channel_freq(channel).is_some() { channel } else { DEFAULT_CHANNEL };
    tune_tcp_for_awdl();
    let _ = Command::new("ip").args(["link", "set", MONITOR, "up"]).status();
    let addr = read_hidden_mac().await?;
    println!("INJECT_MAC {addr:?}");
    set_radio_channel(channel);

    let radio = RadioConstraints { channel: Channel(channel), presence: Presence::Fixed };
    let mut cfg = Config::new(addr, "airdroid-k20", radio);
    // A phone, not a Mac: peers show the phone icon and label.
    cfg.devclass = owly_awdl::tlv::devclass::IOS;
    cfg.rssi_threshold = Some(-85);
    // The K20's WMI management-TX path caps a frame at 1532 bytes. A-MSDU
    // would exceed that and be dropped, so keep one MPDU per injection.
    cfg.max_frame = 1532;
    cfg.rate = TxRate::Legacy(108); /* 54 Mbit/s; kernel sets the actual rate */
    cfg.amsdu = false;
    // Accept the iPhone's A-MPDU Block Ack session. This only helps if the
    // K20 monitor RX path answers the ADDBA handshake, which is what we are
    // measuring here.
    cfg.accept_addba = true;
    // The ACK/retry experiment, see `TX_NO_ACK_DEFAULT`.
    cfg.no_ack = tx_no_ack();
    println!(
        "RADIO_CONFIG no_ack={} rate={} amsdu={} addba={}",
        cfg.no_ack, cfg.rate, cfg.amsdu, cfg.accept_addba
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
    let mdns = Mdns::new(index);
    let mut browse = mdns.browse().await?;
    println!("SCAN_START seconds={seconds} channel={channel}");
    let deadline = Instant::now() + Duration::from_secs(seconds);
    loop {
        tokio::select! {
            event = browse.events.recv() => match event {
                Some(BrowseEvent::Found(found)) => {
                    remember_service_name(&names, &found);
                    println!(
                        "FOUND instance={:?} name={:?} host={:?} addr={} port={} flags=0x{:x}",
                        found.instance, found.name, found.host, found.addr, found.port, found.flags
                    );
                }
                Some(BrowseEvent::Withdrawn(instance)) => {
                    println!("WITHDRAWN instance={instance:?}")
                }
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
        std::slice::from_ref(&path),
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
    paths: &[PathBuf],
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
                        remember_service_name(names, &found);
                        println!(
                            "mDNS FOUND instance={:?} host={:?} addr={} port={} flags=0x{:x}",
                            found.instance, found.host, found.addr, found.port, found.flags
                        );
                        if mdns_matches_target(&found, target_mac) {
                            mdns_found = Some(found);
                        }
                    }
                    Ok(BrowseEvent::Withdrawn(instance)) => {
                        println!("mDNS WITHDRAWN instance={instance:?}");
                        mdns_found = None;
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
        // Prefer the direct AWDL peer address. A cached mDNS address can be
        // stale; trying it first added a full discover timeout before the
        // known peer was even attempted.
        let mut targets: Vec<(SocketAddrV6, u32, &str)> = Vec::new();
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
            let (flags, source) = mdns_found
                .as_ref()
                .filter(|found| {
                    SocketAddrV6::new(found.addr, found.port, 0, index) == addr
                })
                .map_or((RECEIVER_FLAGS | SUPPORTS_DVZIP, "peer"), |found| {
                    (found.flags, "mDNS")
                });
            targets.push((addr, flags, source));
        }
        if let Some(found) = &mdns_found {
            let addr = SocketAddrV6::new(found.addr, found.port, 0, index);
            if !targets.iter().any(|(a, _, _)| *a == addr) {
                targets.push((addr, found.flags, "mDNS"));
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
                        remember_service_name(names, &found);
                        if mdns_matches_target(&found, target_mac) {
                            mdns_found = Some(found);
                        }
                    }
                    Some(BrowseEvent::Withdrawn(_)) | Some(BrowseEvent::Lost(_)) | None => {}
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
    let payload = Payload::Files(paths.to_vec());
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
        name: name.clone(),
        host: host_label(&name, &sender_id(addr)),
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
                Some(ReceiverEvent::FileComplete { id, path }) => {
                    println!(
                        "RECEIVE_FILE_DONE id={id} name={} path={}",
                        display_name(&path),
                        path.display()
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
                                        remember_service_name(&names, &found);
                                        println!(
                                            "FOUND instance={:?} name={:?} host={:?} addr={} port={} flags=0x{:x}",
                                            found.instance, found.name, found.host, found.addr, found.port, found.flags
                                        );
                                    }
                                    Some(BrowseEvent::Withdrawn(instance)) => {
                                        mdns_cache.lock().unwrap().lost(&instance);
                                        println!("WITHDRAWN instance={instance:?}");
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
                    action @ ("send" | "send-batch") => {
                        let id = parts.next().unwrap_or("send").to_string();
                        let mac = parts.next().unwrap_or("").to_string();
                        let arg = parts.next().unwrap_or("");
                        if mac.is_empty() || arg.is_empty() {
                            println!("SEND_ERR {id} missing target or path");
                            continue;
                        }
                        let paths: Vec<PathBuf> = if action == "send-batch" {
                            match serde_json::from_str::<Vec<String>>(arg) {
                                Ok(values) => values.into_iter().map(PathBuf::from).collect(),
                                Err(e) => {
                                    println!("SEND_ERR {id} invalid path list: {e}");
                                    continue;
                                }
                            }
                        } else {
                            vec![PathBuf::from(arg)]
                        };
                        if paths.is_empty() || paths.iter().any(|path| !path.is_file()) {
                            println!("SEND_ERR {id} one or more paths are not files");
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
                        // One send task is the whole transfer, single or batch.
                        // File sizes are uncompressed; progress totals below are
                        // the compressed upload body. New fields go before
                        // `id=` so the app's existing `total=`-last parsing
                        // keeps working.
                        let source_total = paths.iter().map(|path| file_total(path)).sum();
                        let label = if paths.len() == 1 {
                            display_name(&paths[0])
                        } else {
                            format!("{} files", paths.len())
                        };
                        println!(
                            "SEND_FILE_START source_total={source_total} id={id} name={} total={source_total}",
                            label
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
                                &paths,
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
                                        paths[0].display()
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
                    remember_service_name(&names, &found);
                    println!(
                        "FOUND instance={:?} name={:?} host={:?} addr={} port={} flags=0x{:x}",
                        found.instance, found.name, found.host, found.addr, found.port, found.flags
                    );
                }
                Some(BrowseEvent::Withdrawn(instance)) => {
                    mdns_cache.lock().unwrap().lost(&instance);
                    println!("WITHDRAWN instance={instance:?}");
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
        // It is announced so the UI can show that discovery is paused.
        let transfer_active = send_active.load(Ordering::SeqCst) || !active_receives.is_empty();
        let paused_now = pause_discovery_on_transfer && transfer_active;
        update_transfer_pause(&mut transfer_paused, paused_now);
        let should_advertise = visible_during_transfer || !transfer_active;
        if should_advertise && registration.is_none() {
            registration = Some(mdns.register(service.clone()).await?);
            println!("VISIBILITY visible=true");
        } else if !should_advertise && registration.is_some() {
            registration = None;
            println!("VISIBILITY visible=false");
        }
    }

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
    let mut config = ReceiverConfig::new(name.clone());
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
        name: name.clone(),
        host: host_label(&name, &sender_id(addr)),
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
                Some(ReceiverEvent::FileComplete { id, path }) => {
                    println!(
                        "RECEIVE_FILE_DONE id={id} name={} path={}",
                        display_name(&path),
                        path.display()
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
