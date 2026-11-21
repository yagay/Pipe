package org.schabi.newpipe.views;

import android.util.Base64;

import java.nio.charset.StandardCharsets;

/**
 * Browser-only HLS media transport for the one native Pipe player.
 *
 * No web player controls, seek bars, menus, gesture handlers or navigation.
 * The Pipe PlayerBinding owns all visible UI. This document is only the video
 * element, HLS.js, and the small command/state bridge.
 */
public final class SupjavBrowserHlsDocument {
    private SupjavBrowserHlsDocument() { }

    public static String render(final String manifest) {
        final String encoded = Base64.encodeToString(
                manifest.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        return """
                <!doctype html><html><head>
                <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
                <style>
                  *{box-sizing:border-box}
                  html,body{margin:0;width:100%;height:100%;background:#000;overflow:hidden}
                  video{display:block;width:100%;height:100%;object-fit:contain;background:#000}
                </style></head><body>
                <video id="video" playsinline webkit-playsinline></video>
                <script>console.log('PIPE_HLS:BOOTSTRAP');</script>
                <script src="https://cdn.jsdelivr.net/npm/hls.js@1.6.15/dist/hls.min.js"
                  onload="console.log('PIPE_HLS:SCRIPT_LOADED')"
                  onerror="console.log('PIPE_HLS:SCRIPT_FAILED')"></script>
                <script>
                (function(){
                  'use strict';
                  var url=atob('__MEDIA_BASE64__');
                  var video=document.getElementById('video');
                  var hls=null,ready=false,qualityIndex=-1;
                  function report(code){console.log('PIPE_HLS:'+code);}
                  function fail(code){report('ERROR:'+code);}
                  window.PipeHls={
                    play:function(){video.play().catch(function(){});},
                    pause:function(){video.pause();},
                    toggle:function(){if(video.paused)video.play().catch(function(){});
                      else video.pause();},
                    seek:function(delta){if(isFinite(video.duration)){
                      video.currentTime=Math.max(0,Math.min(video.duration,
                        video.currentTime+Number(delta)));}},
                    seekFraction:function(f){if(isFinite(video.duration)&&video.duration>0){
                      video.currentTime=Math.max(0,Math.min(1,Number(f)))*video.duration;}},
                    speed:function(rate){if(rate>0&&rate<=16)video.playbackRate=rate;},
                    quality:function(index){
                      if(!hls||!Number.isInteger(index)||index< -1||
                         index>=hls.levels.length)return;
                      qualityIndex=index;hls.currentLevel=index;
                      report('QUALITY:'+(index<0?'Auto':
                        (hls.levels[index].height||'?')+'p'));
                    },
                    cycleQuality:function(){
                      if(!hls||!hls.levels.length)return;
                      this.quality(qualityIndex>=hls.levels.length-1?-1:qualityIndex+1);
                    }
                  };
                  // The "play" event is only a play REQUEST; the video may be
                  // stalled on its first segment with no rendered frame.
                  // Keep Pipe buffering until decoded video data actually exists.
                  var firstFrame=false;
                  function frameReady(source){
                    if(firstFrame)return;
                    if(video.videoWidth>0&&video.videoHeight>0){
                      firstFrame=true;
                      report('FIRST_FRAME:'+video.videoWidth+':'+video.videoHeight+':'+source);
                    }
                  }
                  video.addEventListener('loadedmetadata',function(){
                    report('METADATA:'+video.videoWidth+':'+video.videoHeight);
                  });
                  video.addEventListener('loadeddata',function(){
                    report('READY');frameReady('loadeddata');
                  });
                  video.addEventListener('playing',function(){
                    report('PLAYING');frameReady('playing');
                    if(typeof video.requestVideoFrameCallback==='function'){
                      video.requestVideoFrameCallback(function(){frameReady('callback');});
                    }
                  });
                  video.addEventListener('waiting',function(){report('WAITING');});
                  video.addEventListener('stalled',function(){report('STALLED');});
                  video.addEventListener('ended',function(){report('ENDED');});
                  video.addEventListener('error',function(){
                    fail('video-element-'+(video.error?video.error.code:'unknown'));
                  });
                  setInterval(function(){
                    if(video.readyState>0){
                      report('POSITION:'+Number(video.currentTime||0).toFixed(2)+':'+
                        Number(isFinite(video.duration)?video.duration:0).toFixed(2)+
                        ':'+(video.paused?'1':'0'));
                    }
                  },1000);
                  if(window.Hls&&Hls.isSupported()){
                    hls=new Hls({enableWorker:false,maxBufferLength:20});
                    var networkRetries=0,mediaRetries=0;
                    hls.on(Hls.Events.MEDIA_ATTACHED,function(){hls.loadSource(url);});
                    hls.on(Hls.Events.MANIFEST_PARSED,function(){
                      ready=true;report('MANIFEST_PARSED');
                      var highest=-1,score=-1;
                      hls.levels.forEach(function(level,index){
                        var value=(level.height||0)*1000000+(level.bitrate||0);
                        if(value>score){score=value;highest=index;}
                      });
                      report('LEVELS:'+hls.levels.map(function(level){
                        return level.height||0;}).join(','));
                      if(highest>=0){
                        qualityIndex=highest;hls.currentLevel=highest;
                        report('QUALITY:'+(hls.levels[highest].height||'?')+'p');
                      }else{report('QUALITY:Auto');}
                      video.play().catch(function(){report('AUTOPLAY_RESTRICTED');});
                    });
                    hls.on(Hls.Events.ERROR,function(event,error){
                      if(!error||!error.fatal)return;
                      var status=error.response&&error.response.code||0;
                      if(error.type===Hls.ErrorTypes.NETWORK_ERROR&&
                         status!==401&&status!==403&&status!==404&&networkRetries++<2){
                        report('NETWORK_RETRY');hls.startLoad();return;
                      }
                      if(error.type===Hls.ErrorTypes.MEDIA_ERROR&&mediaRetries++<1){
                        report('MEDIA_RECOVERY');hls.recoverMediaError();return;
                      }
                      fail('hls-'+(status||error.type||'unknown'));
                      hls.destroy();hls=null;
                    });
                    hls.attachMedia(video);
                  }else if(video.canPlayType('application/vnd.apple.mpegurl')){
                    video.src=url;
                    video.addEventListener('loadedmetadata',function(){
                      ready=true;report('MANIFEST_PARSED');
                      video.play().catch(function(){report('AUTOPLAY_RESTRICTED');});
                    });
                  }else{fail('hlsjs-unavailable');}
                  setTimeout(function(){if(!ready)fail('manifest-timeout');},14000);
                  setTimeout(function(){
                    if(!firstFrame && video.readyState<2) fail('no-video-frames');
                  },18000);
                })();
                </script></body></html>
                """.replace("__MEDIA_BASE64__", encoded);
    }
}
