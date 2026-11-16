package com.vane.browser.core;

/** 注入页面的嗅探脚本：抓取 m3u8 / mp4 / flv / dash 等真实媒体地址。 */
public final class VideoSniffer {

    private VideoSniffer() {}

    public static final String JS = "(function(){" +
            "if(window.__vaneSniff)return;window.__vaneSniff=1;" +
            "var B=window.VaneBridge||null;if(!B)return;" +
            "var re=/\\.(m3u8|mp4|flv|mkv|webm|m4a|mp3|mpd|mov)(\\?|#|$)/i;" +
            "function send(u,t){try{if(!u)return;u=String(u);if(u.indexOf('blob:')===0||u.indexOf('data:')===0)return;" +
            "if(re.test(u)||u.indexOf('m3u8')>-1||u.indexOf('playlist')>-1){B.media(location.href?new URL(u,location.href).href:u,t);}}catch(e){}}" +
            // hook fetch
            "var of=window.fetch;if(of){window.fetch=function(){var a=arguments;try{var u=(a[0]&&a[0].url)||a[0];send(u,'fetch');}catch(e){}return of.apply(this,a);};}" +
            // hook XHR
            "var oo=XMLHttpRequest.prototype.open;XMLHttpRequest.prototype.open=function(m,u){try{send(u,'xhr');}catch(e){}return oo.apply(this,arguments);};" +
            // scan existing media tags + performance entries
            "function scan(){try{var vs=document.querySelectorAll('video,audio,source');for(var i=0;i<vs.length;i++){send(vs[i].currentSrc||vs[i].src,'tag');" +
            "var ss=vs[i].querySelectorAll('source');for(var j=0;j<ss.length;j++)send(ss[j].src,'source');}" +
            "var pe=performance.getEntriesByType?(performance.getEntriesByType('resource')||[]):[];for(var k=0;k<pe.length&&k<800;k++)send(pe[k].name,'res');}catch(e){}}" +
            "setTimeout(scan,800);setTimeout(scan,2500);setTimeout(scan,6000);" +
            "try{new MutationObserver(function(m){m.forEach(function(x){x.addedNodes&&x.addedNodes.forEach(function(n){if(n.tagName==='VIDEO'||n.tagName==='SOURCE')send(n.src||n.currentSrc,'mut');});});}).observe(document.documentElement,{childList:true,subtree:true});}catch(e){}" +
            "window.addEventListener('load',scan);" +
            "})();";

    /** 长按链接：把链接地址回传给原生。 */
    public static final String LONG_PRESS_JS = "(function(){if(window.__vaneLP)return;window.__vaneLP=1;" +
            "document.addEventListener('contextmenu',function(e){var a=e.target&&e.target.closest?e.target.closest('a'):null;" +
            "if(a&&a.href&&a.href.indexOf('vane:')===0)return;" +
                    "if(a&&window.VaneBridge)window.VaneBridge.longPress(a.href,a.textContent||'');},true);})();";

    /** 夜间模式：整页反色过滤（轻量、对所有站点生效）。 */
    public static final String NIGHT_JS = "(function(){if(document.getElementById('__vane_night'))return;" +
            "var s=document.createElement('style');s.id='__vane_night';" +
            "s.textContent='html{filter:invert(1) hue-rotate(180deg);background:#111!important}'+" +
            "'img,video,canvas,svg,[style*=\"background-image\"]{filter:invert(1) hue-rotate(180deg)!important}';" +
            "document.documentElement.appendChild(s);})();";

    /**
     * 强制深色：直接写一套深色样式（Via 的「强制网页暗色」就是这个思路）。
     * 不反色图片，只把背景压深、文字提亮，所以不会变鬼片。
     */
    public static final String DARK_CSS = "(function(){"
            + "var css=':root{color-scheme:dark !important}"
            + "html,body{background:#101114 !important;background-image:none !important;color:#E6E7EA !important}"
            + "*{background-image:none !important}"
            + "*:not(img):not(video):not(canvas):not(svg){background-color:transparent !important}"
            + "*::before,*::after{background-color:transparent !important;background-image:none !important}"
            + "body,body *{color:#E6E7EA !important;border-color:#2A2D34 !important}"
            + "a,a *{color:#8AB4FF !important}"
            + "input,textarea,select,button{background-color:#1A1C21 !important;color:#E6E7EA !important;"
            + "border:1px solid #2A2D34 !important}"
            + "iframe{background:#101114 !important;color-scheme:dark !important}';"
            + "function apply(doc){if(!doc||!doc.documentElement)return;"
            + "if(doc.getElementById('__vane_dark'))return;"
            + "var st=doc.createElement('style');st.id='__vane_dark';st.textContent=css;"
            + "(doc.head||doc.documentElement).appendChild(st);}"
            + "apply(document);"
            + "try{var fs=document.querySelectorAll('iframe');"
            + "for(var i=0;i<fs.length;i++){try{apply(fs[i].contentDocument);}catch(e){}}}catch(e){}"
            + "})();";

    public static final String DARK_OFF = "(function(){var s=document.getElementById('__vane_dark');if(s)s.parentNode.removeChild(s);})();";

    /** 广告点选：高亮可点元素，点一下把选择器交给原生。 */
    public static final String PICKER_JS = "(function(){if(window.__gPick)return;window.__gPick=1;"
            + "var old=null;"
            + "function sel(el){if(!el||el===document.body)return '';if(el.id)return '#'+el.id;"
            + "var t=el.tagName.toLowerCase();var c=(typeof el.className==='string'?el.className:'').trim().split(/\\s+/)[0];"
            + "return c?t+'.'+c:t;}"
            + "document.addEventListener('mouseover',function(e){if(old)old.style.outline='';old=e.target;"
            + "if(old&&old.style){old.style.outline='2px solid #ff5252';}},true);"
            + "document.addEventListener('click',function(e){e.preventDefault();e.stopPropagation();"
            + "var s=sel(e.target);window.__gPick=0;"
            + "if(s&&window.VaneBridge&&VaneBridge.element)VaneBridge.element(s,'点选广告');},true);"
            + "})();";

    /** 登录表单捕获：提交或点登录按钮时把账号密码交给原生。 */
    public static final String PASSWORD_CAPTURE_JS = "(function(){if(window.__gPw)return;window.__gPw=1;"
            + "function grab(){var p=document.querySelector('input[type=password]');if(!p)return null;"
            + "var f=p.closest('form')||document;"
            + "var u=f.querySelector('input[type=text],input[type=email],input:not([type])');"
            + "return {u:u?u.value:'',p:p.value};}"
            + "document.addEventListener('submit',function(){var c=grab();"
            + "if(c&&c.p&&window.VaneBridge&&VaneBridge.pw)VaneBridge.pw(c.u,c.p);},true);"
            + "document.addEventListener('click',function(e){var t=e.target;"
            + "var txt=(t&&t.innerText)||'';"
            + "if(/登录|登陆|sign\\s?in|log\\s?in/i.test(txt)){var c=grab();"
            + "if(c&&c.p&&window.VaneBridge&&VaneBridge.pw)VaneBridge.pw(c.u,c.p);}},true);"
            + "window.__gFill=function(u,p){var ps=document.querySelectorAll('input[type=password]');"
            + "if(!ps.length)return;var pw=ps[0];var f=pw.closest('form')||document;"
            + "var us=f.querySelector('input[type=text],input[type=email],input:not([type])');"
            + "if(us&&u){us.value=u;us.dispatchEvent(new Event('input',{bubbles:true}));}"
            + "if(p){pw.value=p;pw.dispatchEvent(new Event('input',{bubbles:true}));}};"
            + "})();";

    /** 长按任意元素 1.6 秒 → 传回选择器，用于「隐藏这个元素」。 */
    public static final String ELEMENT_JS = "(function(){if(window.__vaneEl)return;window.__vaneEl=1;"
            + "var t=null,target=null;"
            + "function sel(el){if(!el||el===document.body)return '';"
            + "if(el.id)return '#'+el.id;"
            + "var p=el.tagName.toLowerCase();"
            + "if(!el.className||typeof el.className!=='string')return p;"
            + "var c=el.className.trim().split(/\\s+/).filter(Boolean).slice(0,2).join('.');"
            + "return c?p+'.'+c:p;}"
            + "document.addEventListener('touchstart',function(e){target=e.target;"
            + "t=setTimeout(function(){var s=sel(target);"
            + "if(s&&window.VaneBridge&&VaneBridge.element)VaneBridge.element(s,(target.innerText||'').slice(0,40));"
            + "},1600);},{passive:true});"
            + "['touchend','touchmove','touchcancel','scroll'].forEach(function(ev){"
            + "document.addEventListener(ev,function(){if(t){clearTimeout(t);t=null;}},{passive:true});});"
            + "})();";

    /** 长按图片 0.6 秒 → 交给原生弹菜单。 */
    public static final String IMAGE_JS = "(function(){if(window.__vaneImg)return;window.__vaneImg=1;"
            + "var t=null,cur=null;"
            + "document.addEventListener('touchstart',function(e){"
            + "var el=e.target;while(el&&el.tagName!=='IMG')el=el.parentElement;"
            + "if(!el)return;cur=el;t=setTimeout(function(){"
            + "if(window.VaneBridge&&VaneBridge.image)VaneBridge.image(cur.currentSrc||cur.src,cur.alt||'');"
            + "},600);},{passive:true});"
            + "['touchend','touchmove','touchcancel','scroll'].forEach(function(ev){"
            + "document.addEventListener(ev,function(){if(t){clearTimeout(t);t=null;}},{passive:true});});"
            + "})();";

    /** 正文提取：按文本量/段落数给候选块打分，挑最高分重排成文章页。 */
    public static final String READ_EXTRACT_JS =
            "(function(){try{"
            + "var best=null,bestScore=0;"
            + "var cands=document.querySelectorAll('article,main,div,section');"
            + "for(var i=0;i<cands.length;i++){var el=cands[i];"
            + "if(el.offsetHeight<120)continue;"
            + "var ps=el.getElementsByTagName('p');var txt='';"
            + "for(var k=0;k<ps.length;k++)txt+=ps[k].textContent;"
            + "if(txt.length<200)continue;"
            + "var links=el.getElementsByTagName('a');var lk=0;"
            + "for(var k2=0;k2<links.length;k2++)lk+=links[k2].textContent.length;"
            + "var dense=txt.length>0?lk/txt.length:1;"
            + "if(dense>0.4)continue;"
            + "var score=txt.length+ps.length*60-el.getElementsByTagName('input').length*80;"
            + "if(score>bestScore){bestScore=score;best=el;}}"
            + "if(!best)return 0;"
            + "var t=document.title||'';"
            + "var out='';var ps2=best.getElementsByTagName('p');"
            + "for(var j=0;j<ps2.length;j++){var x=ps2[j].textContent.trim();"
            + "if(x.length>20)out+='<p>'+x.replace(/</g,'&lt;')+'</p>';}"
            + "var imgs=best.getElementsByTagName('img');"
            + "for(var m=0;m<imgs.length&&m<6;m++){var src=imgs[m].src;"
            + "if(src&&src.indexOf('http')===0){out+='<p><img src=\\''+src+'\\' style=\\'max-width:100%\\'></p>';}}"
            + "if(out.length<200)return 0;"
            + "var head='<head><meta name=viewport content=\\'width=device-width,initial-scale=1\\'></head>';"
            + "var body='<body><div id=__gr style=\\'max-width:760px;margin:0 auto;padding:18px 16px 60px;"
            + "font:18px/1.85 system-ui,sans-serif;color:#111\\'>';"
            + "body+='<h1 style=\\'font-size:22px;margin:0 0 14px\\'>'+t.replace(/</g,'&lt;')+'</h1>';"
            + "document.documentElement.innerHTML=head+body+out+'</div></body>';"
            + "return 1;}catch(e){return 0;}})();";

    /** 阅读模式：在注入前可以追加用户自定义 CSS。 */
    public static String readerJs(String extraCss) {
        if (extraCss == null || extraCss.trim().isEmpty()) return READER_JS;
        return "(function(){var st=document.createElement('style');st.textContent="
                + org.json.JSONObject.quote(extraCss) + ";document.documentElement.appendChild(st);})();"
                + READER_JS;
    }

    /** 关掉网页蒙版滤镜。 */
    public static final String NIGHT_OFF_JS =
            "(function(){var s=document.getElementById('__vane_night');if(s)s.parentNode.removeChild(s);})();";

    /** 阅读模式：剥离干扰元素。 */
    public static final String READER_JS = "(function(){" +
            "['script','style','iframe','nav','header','footer','aside','.ad','.ads','#comment'].forEach(function(sel){" +
            "try{document.querySelectorAll(sel).forEach(function(n){n.style.display='none'});}catch(e){}});" +
            "document.body.style.maxWidth='820px';document.body.style.margin='0 auto';document.body.style.padding='16px';" +
            "document.body.style.fontSize='18px';document.body.style.lineHeight='1.75';" +
            "})();";
}
