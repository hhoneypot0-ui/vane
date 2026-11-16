/* 精简版 Readability：按段落密度 + class/id 权重挑正文容器，清理后重排 */
(function () {
  if (window.__vaneRead) return window.__vaneRead;
  var NEG = /comment|sidebar|footer|foot|nav|menu|share|related|recommend|advert|sponsor|banner|promo|subscribe|popup|modal|cookie|social|widget|breadcrumb|pagination|toolbar|meta|author-bio/i;
  var POS = /article|content|post|entry|main|body|text|story|blog|detail/i;

  function score(el) {
    if (!el || !el.tagName) return -1;
    var tag = el.tagName.toLowerCase();
    if (/^(script|style|noscript|iframe|nav|aside|footer|header|form|button)$/.test(tag)) return -1;
    var ps = el.getElementsByTagName('p');
    var text = '';
    for (var i = 0; i < ps.length; i++) text += (ps[i].textContent || '');
    if (text.length < 140) return -1;
    var links = el.getElementsByTagName('a'), linkLen = 0;
    for (var k = 0; k < links.length; k++) linkLen += (links[k].textContent || '').length;
    var density = text.length > 0 ? linkLen / text.length : 1;
    if (density > 0.42) return -1;
    var s = text.length + ps.length * 55;
    var cls = (el.className && typeof el.className === 'string' ? el.className : '') + ' ' + (el.id || '');
    if (NEG.test(cls)) s -= 380;
    if (POS.test(cls)) s += 220;
    if (tag === 'article') s += 260;
    if (tag === 'main') s += 180;
    s -= el.getElementsByTagName('input').length * 70;
    return s;
  }

  function best() {
    var cands = document.querySelectorAll('article,main,section,div,td');
    var top = null, topScore = 0;
    for (var i = 0; i < cands.length; i++) {
      var s = score(cands[i]);
      if (s > topScore) { topScore = s; top = cands[i]; }
    }
    return top;
  }

  function clean(el) {
    var clone = el.cloneNode(true);
    var drop = clone.querySelectorAll('script,style,noscript,iframe,form,button,input,select,textarea,aside,footer,nav,figure figcaption');
    for (var i = 0; i < drop.length; i++) { if (drop[i].parentNode) drop[i].parentNode.removeChild(drop[i]); }
    // 去掉负权重容器
    var all = clone.querySelectorAll('*');
    for (var k = all.length - 1; k >= 0; k--) {
      var cls = (all[k].className && typeof all[k].className === 'string' ? all[k].className : '') + ' ' + (all[k].id || '');
      if (NEG.test(cls) && all[k].getElementsByTagName('p').length < 2 && all[k].parentNode) {
        all[k].parentNode.removeChild(all[k]);
      }
    }
    return clone;
  }

  window.__vaneRead = function () {
    try {
      var node = best();
      if (!node) return '';
      var art = clean(node);
      var body = art.innerHTML || '';
      if (body.replace(/<[^>]+>/g, '').length < 180) return '';
      var titleEl = document.querySelector('h1') || document.querySelector('title');
      var title = (titleEl ? (titleEl.textContent || '') : document.title || '').trim();
      var html = '<div id="__vane_reader"><h1>' + esc(title) + '</h1>' + body + '</div>';
      return html;
    } catch (e) { return ''; }
  };

  function esc(s) {
    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }
})();
