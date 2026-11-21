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
                  var hls=null,manifestParsed=false,qualityIndex=-1;
                  var decodedData=false,renderedFrame=false,explicitPause=false;
                  var autoplayBlocked=false,automaticAttempts=0,failed=false;
                  function report(code){console.log('PIPE_HLS:'+code);}
                  function fail(code){
                    if(failed||decodedData)return;
                    failed=true;report('ERROR:'+code);
                  }
                  function requestPlay(reason,manual){
                    if(manual){
                      explicitPause=false;autoplayBlocked=false;
                    }else{
                      if(explicitPause||autoplayBlocked||automaticAttempts>=2)return;
                      automaticAttempts++;
                    }
                    report('PLAY_ATTEMPT:'+reason);
                    try{
                      var promise=video.play();
                      if(promise&&typeof promise.catch==='function'){
                        promise.catch(function(error){
                          var name=error&&error.name||'UnknownError';
                          name=String(name).replace(/[^A-Za-z]/g,'').slice(0,32);
                          report('PLAY_REJECTED:'+name);
                          if(name==='NotAllowedError'){
                            autoplayBlocked=true;
                            report('AUTOPLAY_RESTRICTED');
                          }else if(!manual&&decodedData&&!explicitPause&&
                              automaticAttempts<2){
                            setTimeout(function(){requestPlay('after-rejection',false);},250);
                          }
                        });
                      }
                    }catch(e){
                      report('PLAY_REJECTED:SyncException');
                    }
                  }
                  window.PipeHls={
                    play:function(){requestPlay('manual',true);},
                    pause:function(){explicitPause=true;video.pause();},
                    toggle:function(){
                      if(video.paused)requestPlay('manual',true);
                      else{explicitPause=true;video.pause();}
                    },
                    seek:function(delta){if(isFinite(video.duration)){
                      video.currentTime=Math.max(0,Math.min(video.duration,
                        video.currentTime+Number(delta)));}},
                    seekFraction:function(f){if(isFinite(video.duration)&&video.duration>0){
                      video.currentTime=Math.max(0,Math.min(1,Number(f)))*video.duration;}},
                    speed:function(rate){if(rate>0&&rate<=16)video.playbackRate=rate;},
                    quality:function(index){
                      if(!hls||!Number.isInteger(index)||index< -1||
                         index>=hls.levels.length)return;
                      // Match the working upstream Supjav renderer. The
                      // requested quality is used for the next fragment
                      // without forcing a current-buffer flush.
                      qualityIndex=index;hls.nextLevel=index;
                      report('QUALITY:'+(index<0?'Auto':
                        (hls.levels[index].height?
                          hls.levels[index].height+'p':'源画质')));
                    },
                    cycleQuality:function(){
                      if(!hls||!hls.levels.length)return;
                      this.quality(qualityIndex>=hls.levels.length-1?-1:qualityIndex+1);
                    }
                  };
                  // Three different stages: manifest, decoded data, actual
                  // playback. A video play request is NOT a rendered frame.
                  function frameReady(source){
                    if(renderedFrame)return;
                    if(video.videoWidth>0&&video.videoHeight>0){
                      renderedFrame=true;
                      report('FIRST_FRAME:'+video.videoWidth+':'+video.videoHeight+':'+source);
                    }
                  }
                  video.addEventListener('loadedmetadata',function(){
                    report('METADATA:'+video.videoWidth+':'+video.videoHeight);
                  });
                  video.addEventListener('loadeddata',function(){
                    decodedData=true;
                    report('READY');
                    report('DECODED_DATA:'+video.videoWidth+':'+video.videoHeight);
                    if(video.paused&&!explicitPause&&!autoplayBlocked&&automaticAttempts<2){
                      setTimeout(function(){
                        if(video.paused)requestPlay('decoded-data',false);
                      },250);
                    }
                  });
                  video.addEventListener('playing',function(){
                    report('PLAYING');
                    if(typeof video.requestVideoFrameCallback==='function'){
                      video.requestVideoFrameCallback(function(){frameReady('callback');});
                    }
                  });
                  video.addEventListener('timeupdate',function(){
                    if(!video.paused&&video.currentTime>0&&
                       typeof video.requestVideoFrameCallback!=='function'){
                      frameReady('timeupdate');
                    }
                  });
                  video.addEventListener('pause',function(){report('PAUSED');});
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
                    var firstFragmentLoaded=false,firstFragmentBuffered=false;
                    var firstFragRequestLogged=false,nonFatalErrors=0;
                    function safe(value){return String(value||'unknown')
                      .replace(/[^a-zA-Z0-9_-]/g,'').slice(0,48);}
                    hls.on(Hls.Events.MEDIA_ATTACHED,function(){hls.loadSource(url);});
                    hls.on(Hls.Events.LEVEL_LOADED,function(){
                      report('NETWORK_DIAG:LEVEL_LOADED');
                    });
                    hls.on(Hls.Events.FRAG_LOADING,function(){
                      if(!firstFragRequestLogged){
                        firstFragRequestLogged=true;
                        report('NETWORK_DIAG:FIRST_FRAGMENT_REQUESTED');
                      }
                    });
                    hls.on(Hls.Events.FRAG_LOADED,function(){
                      if(!firstFragmentLoaded){
                        firstFragmentLoaded=true;
                        report('NETWORK_DIAG:FIRST_FRAGMENT_LOADED');
                      }
                    });
                    hls.on(Hls.Events.FRAG_BUFFERED,function(){
                      if(!firstFragmentBuffered){
                        firstFragmentBuffered=true;
                        report('NETWORK_DIAG:FIRST_FRAGMENT_BUFFERED');
                      }
                    });
                    hls.on(Hls.Events.MANIFEST_PARSED,function(){
                      manifestParsed=true;report('MANIFEST_PARSED');
                      // Select the HIGHEST variant actually present in THIS
                      // server's manifest. No fixed 1080p/4K assumptions and
                      // no automatic lowering when its bandwidth is slow.
                      // Pick by resolution, breaking ties by bitrate. For a
                      // single variant with no height, use its source bitrate.
                      var highest=-1,bestHeight=-1,bestWidth=-1,bestBitrate=-1;
                      hls.levels.forEach(function(level,index){
                        var width=Number(level.width)||0;
                        var height=Number(level.height)||0;
                        var bitrate=Number(level.bitrate)||0;
                        // In incomplete manifests the height may be known
                        // while width is omitted. Prioritize source height.
                        if(height>bestHeight||(height===bestHeight&&
                          (width>bestWidth||(width===bestWidth&&
                            bitrate>bestBitrate)))){
                          highest=index;bestHeight=height;
                          bestWidth=width;bestBitrate=bitrate;
                        }
                      });
                      report('LEVELS:'+hls.levels.map(function(level){
                        return level.height||0;}).join(','));
                      if(highest>=0){
                        qualityIndex=highest;
                        // Upstream Supjav's working highest-quality mode
                        // selects the next fragment via nextLevel. Do not
                        // force startLevel/loadLevel together at startup.
                        hls.nextLevel=highest;
                        var selectedLevel=hls.levels[highest];
                        report('QUALITY:'+(selectedLevel.height?
                          selectedLevel.height+'p':'源画质'));
                        report('NETWORK_DIAG:SOURCE_HIGHEST_LEVEL_'+highest
                          +'_HEIGHT_'+(selectedLevel.height||0)
                          +'_BITRATE_'+(selectedLevel.bitrate||0));
                      }else{
                        // A direct/source-only manifest exposes no alternatives.
                        report('QUALITY:源画质');
                        report('NETWORK_DIAG:NO_VARIANT_LEVELS');
                      }
                      requestPlay('manifest',false);
                    });
                    hls.on(Hls.Events.ERROR,function(event,error){
                      if(!error)return;
                      var status=error.response&&error.response.code||0;
                      // Nonfatal fragment/level errors can loop while the video
                      // remains black. Report bounded diagnostics without URLs.
                      if(!error.fatal){
                        if(nonFatalErrors++<6){
                          report('NETWORK_DIAG:NONFATAL_'+safe(error.details)
                            +'_'+Number(status||0));
                        }
                        return;
                      }
                      report('NETWORK_DIAG:FATAL_'+safe(error.details)
                        +'_'+Number(status||0));
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
                      manifestParsed=true;report('MANIFEST_PARSED');
                      requestPlay('native-hls',false);
                    });
                  }else{fail('hlsjs-unavailable');}
                  setTimeout(function(){
                    if(!manifestParsed)fail('manifest-timeout');
                  },20000);
                  // The diagnostic showed the first decoded frame arrived
                  // ~26 seconds after the manifest; a 12s startLoad can
                  // restart a valid in-flight fragment, and an 18/30s fatal
                  // deadline races the CDN. Log stalls; let HLS.js finish.
                  setTimeout(function(){
                    if(!decodedData){
                      report('NETWORK_DIAG:STARTUP_25S_NO_DATA_FRAG_'
                        +Number(typeof firstFragmentLoaded!=='undefined'&&firstFragmentLoaded));
                    }
                  },25000);
                  setTimeout(function(){
                    if(!decodedData){
                      report('NETWORK_DIAG:VIDEO_STATE_'+video.readyState
                        +'_FRAG_'+Number(typeof firstFragmentLoaded!=='undefined'&&firstFragmentLoaded)
                        +'_BUFFERED_'+Number(typeof firstFragmentBuffered!=='undefined'&&firstFragmentBuffered));
                      fail('no-decoded-data-after-75s');
                    }else if(video.paused){
                      report('READY_BUT_PAUSED');
                    }
                  },75000);
                })();
                </script></body></html>
                """.replace("__MEDIA_BASE64__", encoded);
    }
}
