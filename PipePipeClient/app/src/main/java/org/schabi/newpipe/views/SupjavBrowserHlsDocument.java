package org.schabi.newpipe.views;

import android.util.Base64;

import java.nio.charset.StandardCharsets;

/**
 * Shared browser-network HLS renderer for any Supjav provider that exposes
 * an actual HLS request. Adapted from the standalone Supjav
 * BrowserHlsDocument / EmbeddedHlsPlayer design.
 *
 * The real provider WebView discovers URLs; this renderer never manufactures
 * signed media URLs, fetches via Media3, or copies unrelated site cookies.
 */
public final class SupjavBrowserHlsDocument {
    private SupjavBrowserHlsDocument() { }

    public static String render(final String manifest) {
        final String encoded = Base64.encodeToString(
                manifest.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        return """
                <!doctype html>
                <html><head><meta name="viewport"
                content="width=device-width,initial-scale=1,maximum-scale=1">
                <style>
                *{box-sizing:border-box}body{background:#000;color:#fff;margin:0;
                font:14px system-ui,sans-serif;overflow:hidden}
                #wrap{position:fixed;inset:0;background:#000;display:flex;
                align-items:center;justify-content:center}
                video{width:100%;height:100%;object-fit:contain}
                #controls{display:none!important}
                #controls{position:absolute;bottom:0;left:0;right:0;padding:12px 14px 20px;
                background:linear-gradient(transparent,rgba(0,0,0,.87));display:flex;
                gap:10px;align-items:center;flex-wrap:wrap}
                button,select{background:rgba(36,36,36,.85);color:#fff;border:0;
                border-radius:7px;padding:8px;min-height:36px}
                #seek{flex:1;min-width:110px;accent-color:#fff}
                #status{position:absolute;top:10px;left:12px;right:12px;
                pointer-events:none;color:#ddd;text-shadow:0 1px 3px #000}
                </style></head><body>
                <div id="wrap"><video id="video" playsinline webkit-playsinline
                controlslist="nodownload"></video>
                <div id="status">Loading HLS…</div>
                <div id="controls">
                  <button id="toggle" aria-label="Play or pause">❚❚</button>
                  <button id="back" aria-label="Back 10 seconds">−10</button>
                  <input id="seek" type="range" min="0" max="1000" value="0">
                  <button id="forward" aria-label="Forward 10 seconds">+10</button>
                  <span id="clock">0:00 / 0:00</span>
                  <select id="quality" aria-label="Video quality"><option value="-1">Auto</option></select>
                  <select id="speed" aria-label="Playback speed">
                    <option value=".75">0.75×</option><option value="1" selected>1×</option>
                    <option value="1.25">1.25×</option><option value="1.5">1.5×</option>
                    <option value="2">2×</option></select>
                  <button id="fullscreen" aria-label="Fullscreen">⛶</button>
                </div></div>
                <script src="https://cdn.jsdelivr.net/npm/hls.js@1.6.15/dist/hls.min.js"></script>
                <script>
                (function(){
                 'use strict';
                 var url=atob('__MEDIA_BASE64__');
                 var video=document.getElementById('video'),hls=null;
                 var status=document.getElementById('status');
                 var seek=document.getElementById('seek');
                 var toggle=document.getElementById('toggle');
                 var quality=document.getElementById('quality');
                 var initialized=false;
                 function log(code){console.log('PIPE_HLS:'+code);}
                 function reportError(code){status.textContent='Playback failed: '+code;
                   log('ERROR:'+code);}
                 function clock(s){if(!isFinite(s)||s<0)return '0:00';
                   s=Math.floor(s);return Math.floor(s/60)+':'+('0'+s%60).slice(-2);}
                 // Shared native player UI receives progress and drives the same
                // video element through a single command interface.
                var qualityIndex=-1;
                window.PipeHls={
                  toggle:function(){if(video.paused)video.play().catch(function(){});
                    else video.pause();},
                  seek:function(delta){if(isFinite(video.duration)){
                    video.currentTime=Math.max(0,Math.min(video.duration,
                      video.currentTime+delta));}},
                  seekFraction:function(fraction){if(isFinite(video.duration)
                    &&video.duration>0)video.currentTime=video.duration*
                      Math.max(0,Math.min(1,fraction));},
                  speed:function(rate){video.playbackRate=rate;},
                  cycleQuality:function(){if(!hls||!hls.levels.length)return;
                    qualityIndex=qualityIndex>=hls.levels.length-1?-1:qualityIndex+1;
                    hls.currentLevel=qualityIndex;
                    var label=qualityIndex<0?'Auto':
                      (hls.levels[qualityIndex].height||'?')+'p';
                    log('QUALITY:'+label);},
                  pause:function(){video.pause();}
                };
                function reportPosition(){
                  if(video.readyState>0){
                    log('POSITION:'+Number(video.currentTime||0).toFixed(2)+':'+
                      Number(isFinite(video.duration)?video.duration:0).toFixed(2)+
                      ':'+(video.paused?'1':'0'));
                  }
                }
                setInterval(reportPosition,1000);
                function update(){if(isFinite(video.duration)&&video.duration>0){
                   seek.value=Math.round(video.currentTime/video.duration*1000);
                 }document.getElementById('clock').textContent=clock(video.currentTime)+
                   ' / '+clock(video.duration);
                   toggle.textContent=video.paused?'▶':'❚❚';}
                 video.addEventListener('timeupdate',update);
                 video.addEventListener('durationchange',update);
                 video.addEventListener('play',function(){status.textContent='';log('PLAYING');update();});
                 video.addEventListener('error',function(){reportError('video-element');});
                 toggle.onclick=function(){if(video.paused)video.play().catch(function(){});
                   else video.pause();};
                 document.getElementById('back').onclick=function(){
                   video.currentTime=Math.max(0,video.currentTime-10);};
                 document.getElementById('forward').onclick=function(){
                   if(isFinite(video.duration))video.currentTime=Math.min(video.duration,video.currentTime+10);};
                 seek.oninput=function(){if(isFinite(video.duration)&&video.duration>0)
                   video.currentTime=video.duration*Number(seek.value)/1000;};
                 document.getElementById('speed').onchange=function(){
                   video.playbackRate=Number(this.value);};
                 document.getElementById('fullscreen').onclick=function(){
                   if(!document.fullscreenElement){document.getElementById('wrap').requestFullscreen();}
                   else document.exitFullscreen();};
                 quality.onchange=function(){if(hls)hls.currentLevel=Number(this.value);};
                 if(window.Hls&&Hls.isSupported()){
                   hls=new Hls({enableWorker:false,maxBufferLength:20});
                   var networkRetries=0,mediaRetries=0;
                   hls.on(Hls.Events.MEDIA_ATTACHED,function(){hls.loadSource(url);});
                   hls.on(Hls.Events.MANIFEST_PARSED,function(){
                     initialized=true;log('READY');status.textContent='';
                     quality.innerHTML='<option value="-1">Auto</option>';
                     hls.levels.forEach(function(level,i){
                       if(!level.height)return;
                       var o=document.createElement('option');o.value=i;
                       o.textContent=level.height+'p';quality.appendChild(o);
                     });
                     video.play().catch(function(){status.textContent='Tap play to start';});
                   });
                   hls.on(Hls.Events.ERROR,function(evt,error){
                     if(!error||!error.fatal)return;
                     var code=error.response&&error.response.code||0;
                     if(error.type===Hls.ErrorTypes.NETWORK_ERROR&&
                        code!==401&&code!==403&&code!==404&&networkRetries++<2){
                       log('NETWORK_RETRY');hls.startLoad();return;
                     }
                     if(error.type===Hls.ErrorTypes.MEDIA_ERROR&&mediaRetries++<1){
                       log('MEDIA_RECOVERY');hls.recoverMediaError();return;
                     }
                     reportError('hls-'+(code||error.type||'unknown'));
                     hls.destroy();hls=null;
                   });
                   hls.attachMedia(video);
                 }else if(video.canPlayType('application/vnd.apple.mpegurl')){
                   video.src=url;video.addEventListener('loadedmetadata',function(){
                     initialized=true;log('READY');status.textContent='';
                     video.play().catch(function(){});});
                 }else{reportError('hlsjs-unavailable');}
                 setTimeout(function(){if(!initialized)reportError('startup-timeout');},14000);
                })();
                </script></body></html>
                """.replace("__MEDIA_BASE64__", encoded);
    }
}
