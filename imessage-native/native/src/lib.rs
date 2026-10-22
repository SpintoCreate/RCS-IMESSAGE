//! Narrow Android ABI probe. This does not activate an Apple account or send messages.
//! It intentionally returns no serials, UDIDs, validation blobs, or relay response bodies.

use std::{panic::AssertUnwindSafe, time::Duration};

use jni::{objects::{JObject, JString}, sys::jstring, JNIEnv};
use rustpush::{PushError, RelayConfig};
use tokio::{runtime::Runtime, time::timeout};
use url::Url;

fn valid_relay_url(input: &str) -> bool {
    Url::parse(input).is_ok_and(|url| {
        url.scheme() == "https"
            && url.host_str().is_some()
            && url.username().is_empty()
            && url.password().is_none()
            && url.query().is_none()
            && url.fragment().is_none()
            && url.path() == "/"
    })
}

fn probe_relay(endpoint: &str, code: &str) -> &'static str {
    if !valid_relay_url(endpoint) {
        return "invalid-endpoint";
    }
    if code.trim().is_empty() || code.len() > 128 {
        return "invalid-code";
    }
    let Ok(runtime) = Runtime::new() else { return "runtime-unavailable" };
    let token = None;
    match runtime.block_on(timeout(
        Duration::from_secs(25),
        RelayConfig::get_versions(endpoint.trim_end_matches('/'), code, &token),
    )) {
        Ok(Ok(_)) => "relay-reachable",
        Ok(Err(PushError::RelayError(401 | 403, _))) => "invalid-code",
        Ok(Err(PushError::DeviceNotFound)) => "relay-offline",
        Ok(Err(_)) => "relay-error",
        Err(_) => "relay-timeout",
    }
}

fn return_string(env: &mut JNIEnv, value: &str) -> jstring {
    env.new_string(value).map_or(std::ptr::null_mut(), |result| result.into_raw())
}

#[no_mangle]
pub extern "system" fn Java_com_unifiedmessages_imessage_RustpushBridge_nativeEngineVersion(
    mut env: JNIEnv,
    _self: JObject,
) -> jstring {
    let _linked_engine = std::mem::size_of::<RelayConfig>();
    return_string(&mut env, "rustpush-a7fab473-abi1")
}

#[no_mangle]
pub extern "system" fn Java_com_unifiedmessages_imessage_RustpushBridge_nativeProbeRelay(
    mut env: JNIEnv,
    _self: JObject,
    endpoint: JString,
    code: JString,
) -> jstring {
    let result = std::panic::catch_unwind(AssertUnwindSafe(|| {
        let endpoint: String = env.get_string(&endpoint).map_err(|_| ())?.into();
        let code: String = env.get_string(&code).map_err(|_| ())?.into();
        Ok::<_, ()>(probe_relay(&endpoint, &code))
    }));
    let status = result.ok().and_then(Result::ok).unwrap_or("native-error");
    return_string(&mut env, status)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rejects_plaintext_or_credential_bearing_relay_urls() {
        assert!(!valid_relay_url("http://relay.example"));
        assert!(!valid_relay_url("https://user:secret@relay.example"));
        assert!(!valid_relay_url("https://relay.example/?token=secret"));
        assert!(valid_relay_url("https://relay.example/"));
    }
}
