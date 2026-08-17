#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .on_page_load(|webview, payload| {
            if payload.event() == tauri::webview::PageLoadEvent::Finished {
                let _ = webview.eval(PREFER_VIDEO_FULLSCREEN_JS);
            }
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}

/// If the page fullscreens <html>/<body>, Android shows the whole app UI.
/// Redirect that to the <video> (or its player root) instead.
const PREFER_VIDEO_FULLSCREEN_JS: &str = r#"
(function () {
  if (window.__zhiyuanPreferVideoFs) return;
  window.__zhiyuanPreferVideoFs = true;
  function isPageRoot(el) {
    return !el || el === document.documentElement || el === document.body ||
      el.tagName === 'HTML' || el.tagName === 'BODY';
  }
  function playerTarget(el) {
    if (isPageRoot(el)) {
      return document.querySelector('video') || el;
    }
    if (el && el.tagName === 'VIDEO') {
      return el.closest('.art-video-player, .artplayer, .dplayer, .xgplayer, .video-js') || el;
    }
    if (el && el.querySelector) {
      var video = el.querySelector('video');
      if (video) {
        var page = document.documentElement;
        var er = el.getBoundingClientRect();
        if (page && er.width >= page.clientWidth * 0.95 && er.height >= page.clientHeight * 0.95) {
          return video.closest('.art-video-player, .artplayer, .dplayer, .xgplayer, .video-js') || video;
        }
      }
    }
    return el;
  }
  function wrap(proto, name) {
    var orig = proto[name];
    if (typeof orig !== 'function') return;
    proto[name] = function () {
      var target = playerTarget(this);
      if (target && target !== this && typeof target[name] === 'function') {
        return target[name].apply(target, arguments);
      }
      return orig.apply(this, arguments);
    };
  }
  wrap(Element.prototype, 'requestFullscreen');
  wrap(Element.prototype, 'webkitRequestFullscreen');
  wrap(Element.prototype, 'webkitRequestFullScreen');
})();
"#;
