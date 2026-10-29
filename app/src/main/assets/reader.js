// SPDX-License-Identifier: GPL-3.0-or-later
// Injected into every chapter page. Lays the chapter out as columns (one or two per page),
// turns pages, and reports tapped words to the app through the SY bridge.
var sy = (function () {
  var cfg, W, H, G, K, pages = 1, page = 0, hl = null, filler = null, t0 = null;

  // Book fonts shipped inside the app (Literata, Crimson Text; both SIL Open Font License).
  var FACES = (function () {
    var out = '', fams = { SayfaLiterata: 'literata', SayfaCrimson: 'crimson' };
    var cuts = [['regular', 'normal', 'normal'], ['italic', 'normal', 'italic'], ['bold', 'bold', 'normal'], ['bolditalic', 'bold', 'italic']];
    for (var f in fams) for (var i = 0; i < cuts.length; i++) {
      out += '@font-face{font-family:"' + f + '";src:url("file:///android_asset/font-' + fams[f] + '-' + cuts[i][0] +
        '.ttf");font-weight:' + cuts[i][1] + ';font-style:' + cuts[i][2] + ';}';
    }
    return out;
  })();

  /** Inserts soft hyphens so justified lines break inside long words instead of stretching the spaces. */
  function hyphenate() {
    if (!window.Hypher || !window.HYPH_EN || document.body.getAttribute('data-sy-hy')) return;
    document.body.setAttribute('data-sy-hy', '1');
    var h = new Hypher(HYPH_EN), walker = document.createTreeWalker(document.body, 4, null, false), n, list = [];
    while ((n = walker.nextNode())) list.push(n);
    for (var i = 0; i < list.length; i++) {
      var p = list[i].parentNode, name = p && p.nodeName.toUpperCase();
      if (!name || /^(PRE|CODE|H1|H2|H3|H4|H5|H6|STYLE|SCRIPT|TITLE)$/.test(name)) continue;
      if (list[i].data.length > 5) list[i].data = h.hyphenateText(list[i].data, 6);
    }
  }

  /**
   * Waits (at most ~2.5 s) until the chosen web font has loaded, so pages are counted with the real font.
   * The chapter stays hidden meanwhile: two probe words outside <body> load the font, so a long chapter is
   * laid out once with the right font instead of once per font change.
   */
  function whenFontReady(done) {
    var fam = cfg.font && /Sayfa\w+/.exec(cfg.font);
    if (!fam) return done();
    var host = document.documentElement, probe = document.createElement('span'), test = document.createElement('span');
    probe.style.cssText = test.style.cssText = 'position:absolute;left:-9999px;top:0;font-size:40px;white-space:nowrap;';
    // Marked important so the reader's own "body *" font rule cannot reach them.
    probe.style.setProperty('font-family', 'monospace', 'important');
    test.style.setProperty('font-family', '"' + fam[0] + '",monospace', 'important');
    probe.textContent = test.textContent = 'Hamburgefonstiv 0123';
    host.appendChild(probe); host.appendChild(test);
    var tries = 0;
    (function check() {
      if (test.offsetWidth !== probe.offsetWidth || ++tries > 50) {
        host.removeChild(probe); host.removeChild(test);
        done();
      } else setTimeout(check, 50);
    })();
  }

  /** Lines with a negative first-line indent (index entries) must not start left of the page edge. */
  function fixHangingIndents() {
    var els = document.body.querySelectorAll('p,div,li,dd,dt'), inset = [];
    function leftInset(el) {  // margins and padding between the page edge and el's content
      var sum = 0;
      for (; el && el !== document.body; el = el.parentNode) {
        var cs = getComputedStyle(el);
        sum += parseFloat(cs.marginLeft) + parseFloat(cs.paddingLeft);
      }
      return sum;
    }
    for (var i = 0; i < els.length; i++) {
      var t = getComputedStyle(els[i]).textIndent, ti = parseFloat(t);
      if (!(ti < 0)) continue;
      if (/%$/.test(t)) ti = ti / 100 * els[i].clientWidth;  // KitKat reports "-5%" rather than pixels
      inset.push([els[i], leftInset(els[i]) + ti]);
    }
    for (var j = 0; j < inset.length; j++) {
      if (inset[j][1] < 0) inset[j][0].style.marginLeft = (parseFloat(getComputedStyle(inset[j][0]).marginLeft) - inset[j][1]) + 'px';
    }
  }

  function css(c) {
    var s = document.getElementById('sy-style');
    if (!s) {
      s = document.createElement('style');
      s.id = 'sy-style';
      (document.head || document.documentElement).appendChild(s);
    }
    s.textContent = FACES +
      // The font's size correction goes on <html>: books often scale <body> and undo it on <p>
      // (1.42em × 0.71em), and overriding <body> would break that pair and shrink the text.
      'html{margin:0!important;padding:0!important;overflow:hidden!important;height:' + H + 'px!important;' +
      (c.size ? 'font-size:' + c.size + '%!important;' : '') +
      'background:' + c.bg + '!important;-webkit-text-size-adjust:100%!important;-webkit-user-select:none;-webkit-touch-callout:none;-webkit-tap-highlight-color:rgba(0,0,0,0);}' +
      'body{margin:0!important;padding:0!important;height:' + H + 'px!important;width:' + W + 'px!important;' +
      '-webkit-column-width:' + ((W - (K - 1) * G) / K) + 'px!important;-webkit-column-gap:' + G + 'px!important;' +
      'background:' + c.bg + '!important;color:' + c.fg + '!important;line-height:' + c.lh + '!important;' +
      // No optimizeLegibility/kerning: KitKat's WebView breaks lines without kerning but draws with it,
      // which pushed justified lines past the page edge.
      'text-align:justify;word-wrap:break-word;' +
      (c.font ? 'font-family:' + c.font + '!important;' : '') + '}' +
      'body *{color:inherit!important;background-color:transparent!important;' +
      (c.font ? 'font-family:inherit!important;' : '') + 'max-width:100%;}' +
      'a,a *{color:' + c.link + '!important;text-decoration:none!important;}' +
      'p{margin-top:0!important;margin-bottom:0!important;line-height:inherit!important;}' +
      'p+p{text-indent:1.4em;}' +
      'h1,h2,h3,h4{line-height:1.25!important;text-align:left!important;-webkit-column-break-after:avoid;' +
      'margin:.2em 0 .9em!important;font-weight:bold;}' +
      'h1{font-size:1.6em!important;}h2{font-size:1.35em!important;}h3{font-size:1.15em!important;}' +
      'blockquote{margin:.6em 1.2em!important;}' +
      'img{max-width:100%!important;max-height:' + (H - 24) + 'px!important;height:auto;-webkit-column-break-inside:avoid;}' +
      'svg{display:block;margin:0 auto;-webkit-column-break-inside:avoid;}' +
      '.sy-hl{background-color:' + c.hl + '!important;border-radius:3px;}' +
      '.sy-fill{-webkit-column-break-before:always;height:1px;}';
  }

  /** SVG pictures (often the cover) get a real size that fits one page, keeping their proportions. */
  function fitSvgs() {
    var svgs = document.getElementsByTagName('svg'), colW = (W - (K - 1) * G) / K, maxH = H - 24;
    for (var i = 0; i < svgs.length; i++) {
      var s = svgs[i], vb = s.viewBox && s.viewBox.baseVal, ratio = vb && vb.width ? vb.height / vb.width : 0;
      if (!ratio) {
        var bw = parseFloat(s.getAttribute('width')), bh = parseFloat(s.getAttribute('height'));
        ratio = bw && bh && !/%/.test(s.getAttribute('width') + s.getAttribute('height')) ? bh / bw : 1.5;
      }
      var w = Math.min(colW, maxH / ratio);
      s.style.width = Math.floor(w) + 'px';
      s.style.height = Math.floor(w * ratio) + 'px';
    }
  }

  function measure() {
    var d = document.documentElement, w = Math.max(d.scrollWidth, document.body.scrollWidth);
    return Math.max(1, Math.round((w + G) / ((W - (K - 1) * G) / K + G)));
  }

  function layout() {
    W = window.innerWidth; H = window.innerHeight;
    K = (W > 900 && cfg.spread) ? 2 : 1;
    G = K === 2 ? Math.round(W * 0.055) : 0;
    css(cfg);
    fitSvgs();
    if (filler) { filler.parentNode.removeChild(filler); filler = null; }
    var cols = measure();
    if (cols % K) {
      filler = document.createElement('div');
      filler.className = 'sy-fill';
      document.body.appendChild(filler);
      cols = measure();
    }
    pages = Math.max(1, Math.ceil(cols / K));
  }

  // The reading area can change size after the first layout (system bars hiding, rotation):
  // re-measure then, staying at the same place in the chapter.
  var resizeTimer = null;
  window.addEventListener('resize', function () {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(function () {
      if (!cfg || (window.innerWidth === W && window.innerHeight === H)) return;
      var f = pages > 1 ? page / pages : 0;
      layout();
      go(Math.floor(f * pages + 0.0001));
    }, 150);
  }, false);

  function go(p, silent) {
    page = Math.max(0, Math.min(pages - 1, p));
    window.scrollTo(page * (W + G), 0);
    if (!silent) SY.onPage(page, pages);
  }

  function clearHl() {
    if (!hl) return;
    var p = hl.parentNode;
    if (p) {
      while (hl.firstChild) p.insertBefore(hl.firstChild, hl);
      p.removeChild(hl);
      p.normalize();
    }
    hl = null;
  }

  // ­: the soft hyphens hyphenate() inserted are part of the word.
  var WORD = /[A-Za-zÀ-ɏ'’\-­]/;

  function wordAt(x, y) {
    if (!document.caretRangeFromPoint) return null;
    var r = document.caretRangeFromPoint(x, y);
    if (!r || r.startContainer.nodeType !== 3) return null;
    var node = r.startContainer, t = node.data, s = r.startOffset, e = s;
    if (s >= t.length || !WORD.test(t.charAt(s))) {
      if (s > 0 && WORD.test(t.charAt(s - 1))) s = e = s - 1; else return null;
    }
    while (s > 0 && WORD.test(t.charAt(s - 1))) s--;
    while (e < t.length && WORD.test(t.charAt(e))) e++;
    while (s < e && /['’\-]/.test(t.charAt(s))) s++;
    while (e > s && /['’\-]/.test(t.charAt(e - 1))) e--;
    if (e <= s) return null;
    var wr = document.createRange();
    wr.setStart(node, s); wr.setEnd(node, e);
    var rects = wr.getClientRects(), hit = false;
    for (var i = 0; i < rects.length; i++) {
      var b = rects[i];
      if (x >= b.left - 6 && x <= b.right + 6 && y >= b.top - 6 && y <= b.bottom + 6) { hit = true; break; }
    }
    return hit ? { range: wr, word: t.substring(s, e).replace(/­/g, ''), node: node, start: s } : null;
  }

  function sentenceOf(w) {
    var el = w.node.parentNode;
    while (el && el !== document.body && !/^(P|DIV|LI|H\d|BLOCKQUOTE|TD|DD|DT)$/.test(el.nodeName.toUpperCase())) el = el.parentNode;
    if (!el) return '';
    var full = el.textContent || '', pre = 0, n = el.ownerDocument.createTreeWalker(el, 4, null, false), cur;
    while ((cur = n.nextNode())) { if (cur === w.node) break; pre += cur.data.length; }
    var at = pre + w.start, a = at, b = at;
    while (a > 0 && !/[.!?…]/.test(full.charAt(a - 1))) a--;
    while (b < full.length && !/[.!?…]/.test(full.charAt(b))) b++;
    if (b < full.length) b++;
    var s = full.substring(a, b).replace(/­/g, '').replace(/\s+/g, ' ').trim();
    return s.length > 400 ? s.substring(0, 400) + '…' : s;
  }

  function onTap(x, y) {
    var w = wordAt(x, y);
    if (w) {
      clearHl();
      var sentence = sentenceOf(w);
      try {
        hl = document.createElement('span');
        hl.className = 'sy-hl';
        w.range.surroundContents(hl);
      } catch (err) { hl = null; }
      SY.onWord(w.word, sentence, y / H);
      return;
    }
    clearHl();
    SY.onTap(x / W);
  }

  document.addEventListener('touchstart', function (ev) {
    var t = ev.touches[0];
    t0 = { x: t.clientX, y: t.clientY, time: Date.now() };
  }, false);
  document.addEventListener('touchmove', function (ev) { ev.preventDefault(); }, false);
  document.addEventListener('touchend', function (ev) {
    if (!t0) return;
    var t = ev.changedTouches[0], dx = t.clientX - t0.x, dy = t.clientY - t0.y;
    ev.preventDefault();
    if (Math.abs(dx) > 40 && Math.abs(dx) > Math.abs(dy)) {
      if (dx < 0) next(); else prev();
    } else if (Math.abs(dx) < 15 && Math.abs(dy) < 15) {
      var link = ev.target;
      while (link && link.nodeName.toUpperCase() !== 'A') link = link.parentNode;
      if (link && link.getAttribute('href')) SY.onLink(link.href);
      else onTap(t0.x, t0.y);
    }
    t0 = null;
  }, false);

  function next() { if (page < pages - 1) go(page + 1); else SY.onEdge(1); }
  function prev() { if (page > 0) go(page - 1); else SY.onEdge(-1); }

  function anchorPage(id) {
    var el = id && document.getElementById(id);
    if (!el) return -1;
    var left = el.getBoundingClientRect().left + window.pageXOffset;
    return Math.floor(left / (W + G));
  }

  return {
    init: function (c) {
      cfg = c;
      // Without a viewport, KitKat keeps the portrait zoom after turning to landscape (huge text, 1 column).
      var vp = document.querySelector('meta[name=viewport]') || document.createElement('meta');
      vp.name = 'viewport';
      vp.content = 'width=device-width, initial-scale=1, minimum-scale=1, maximum-scale=1, user-scalable=no';
      if (!vp.parentNode) (document.head || document.documentElement).appendChild(vp);
      W = window.innerWidth; H = window.innerHeight; K = 1; G = 0;
      document.body.style.display = 'none';  // no layout work until the font is ready (see whenFontReady)
      css(cfg);
      hyphenate();
      whenFontReady(function () {
        document.body.style.display = '';
        fixHangingIndents();
        layout();
        var p = anchorPage(c.anchor);
        if (p < 0) p = c.frac >= 1 ? pages - 1 : Math.floor(c.frac * pages + 0.0001);
        go(p, true);
        SY.onReady(page, pages);
      });
    },
    relayout: function (frac) { layout(); go(frac >= 1 ? pages - 1 : Math.floor(frac * pages + 0.0001)); },
    next: next, prev: prev, clearHl: clearHl,
    /** Page `p` of a layout that had `n` pages (scaled if the page count has changed since). */
    goPage: function (p, n) { go(n === pages ? p : Math.floor(p / n * pages + 0.0001)); },
    anchor: function (id) { var p = anchorPage(id); if (p >= 0) go(p); },
    /** [page width, page height, gap, content width, pages] in CSS px, for the page-picture strip. */
    geom: function () {
      var d = document.documentElement;
      return [W, H, G, Math.max(d.scrollWidth, document.body.scrollWidth), pages];
    }
  };
})();
