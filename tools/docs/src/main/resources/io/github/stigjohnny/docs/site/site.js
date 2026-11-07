/*
 * The site's one script, generated with the site by tools/docs: the theme switch, the navigation disclosure, the copy
 * button of the brain, diagram enlargement, and Mermaid on the diagram pages, whose drawings it fits to their bounds.
 * It stores nothing, fetches nothing and writes no markup. Without it every page still works: the navigation stays
 * open and the diagrams show their source.
 */
(function () {
  'use strict';

  var root = document.documentElement;

  // Theme: the system's preference, unless the reader chooses Light or Dark; the choice lasts until the page reloads.
  var themeSwitch = document.querySelector('.theme-switch');
  if (themeSwitch) {
    themeSwitch.hidden = false;
    themeSwitch.addEventListener('change', function (event) {
      var choice = event.target && event.target.value;
      if (choice === 'light' || choice === 'dark') {
        root.setAttribute('data-theme', choice);
      } else {
        root.removeAttribute('data-theme');
      }
    });
  }

  // Navigation: a labelled disclosure below 720px, always open from 720px up. When the breakpoint changes, focus stays
  // usable, whether or not the disclosure was already open: a focused navigation that collapses hands focus to its
  // summary, and a focused summary that disappears at desktop width hands it to the current page's link, or the first.
  // The browser can move focus to <body> as soon as the focused element is hidden, before the media query's change
  // event runs, so the script remembers where focus was last; a pointer press outside the navigation forgets it.
  var disclosure = document.querySelector('.site-nav-disclosure');
  if (disclosure && window.matchMedia) {
    var wide = window.matchMedia('(min-width: 720px)');
    var summary = disclosure.querySelector('summary');
    var lastInside = false;
    var lastSummary = false;
    document.addEventListener('focusin', function (event) {
      lastInside = disclosure.contains(event.target);
      lastSummary = summary !== null && event.target === summary;
    });
    document.addEventListener('pointerdown', function (event) {
      if (!disclosure.contains(event.target)) {
        lastInside = false;
        lastSummary = false;
      }
    });
    var syncNavigation = function () {
      var active = document.activeElement;
      var lost = !active || active === document.body;
      var focusInside = disclosure.contains(active) || (lost && lastInside);
      var summaryFocused = (summary !== null && active === summary) || (lost && lastSummary);
      var opening = wide.matches;
      disclosure.open = opening;
      if (!opening && focusInside && summary) {
        summary.focus();
      } else if (opening && summaryFocused) {
        var target = disclosure.querySelector('a[aria-current="page"]') || disclosure.querySelector('a');
        if (target) {
          target.focus();
        }
      }
    };
    syncNavigation();
    if (wide.addEventListener) {
      wide.addEventListener('change', syncNavigation);
    }
  }

  // Copy the agent context; the outcome is announced in words in a status region.
  var copyButton = document.querySelector('[data-copy-target]');
  if (copyButton) {
    var copySource = document.getElementById(copyButton.getAttribute('data-copy-target'));
    var copyStatus = document.getElementById(copyButton.getAttribute('data-status'));
    var report = function (message) {
      if (copyStatus) {
        copyStatus.textContent = message;
      }
    };
    copyButton.hidden = false;
    copyButton.addEventListener('click', function () {
      if (!copySource || !navigator.clipboard || !window.isSecureContext) {
        report('Copying is not available here. Open "Agent context (BRAIN.md)" below and copy the text by hand.');
        return;
      }
      navigator.clipboard.writeText(copySource.textContent).then(function () {
        report('Agent context copied to the clipboard.');
      }, function () {
        report('Copying failed. Open "Agent context (BRAIN.md)" below and copy the text by hand.');
      });
    });
  }

  // Diagram enlargement: a toggle button (aria-pressed); the enlarged diagram scrolls inside its labelled region. The
  // stylesheet widens the drawing to its natural width or one and a half times the region, whichever is wider, within
  // the same fitted bounds.
  Array.prototype.forEach.call(document.querySelectorAll('[data-enlarge]'), function (button) {
    var figure = document.getElementById(button.getAttribute('data-enlarge'));
    if (!figure) {
      return;
    }
    button.hidden = false;
    button.addEventListener('click', function () {
      var enlarged = button.getAttribute('aria-pressed') !== 'true';
      button.setAttribute('aria-pressed', enlarged ? 'true' : 'false');
      figure.classList.toggle('is-enlarged', enlarged);
    });
  });

  // Diagram fitting. Mermaid sizes a drawing from a box that leaves out strokes and arrowheads, so a drawing could be
  // cut at its edges. Once drawn, its viewBox becomes its complete bounds, measured in the drawing's own units: every
  // shape, label and edge with half its stroke, and the arrowhead at each marked end, plus padding. The drawing keeps
  // that aspect ratio at every size, fitted to its region or enlarged. Until then it is invisible: the stylesheet hides
  // the drawing of a figure without data-fitted="true", while the caption and the tables stay, and without the script
  // there is no drawing, only its source. A ResizeObserver on each region fits again when the region's width changes;
  // bounds that changed are applied in the next frame, with the drawing hidden until then, so no frame shows it cut.
  var FIT_PADDING = 16;
  var MARKER_REFERENCE = /#([^"')\s]+)["']?\)/;
  var drawnBounds = function (svg) {
    var screen = svg.getScreenCTM();
    if (!screen || !screen.a) {
      return null;
    }
    var toUser = screen.inverse();
    var bounds = null;
    var include = function (matrix, x, y) {
      var point = new DOMPoint(x, y).matrixTransform(matrix);
      if (bounds === null) {
        bounds = { left: point.x, top: point.y, right: point.x, bottom: point.y };
        return;
      }
      bounds.left = Math.min(bounds.left, point.x);
      bounds.top = Math.min(bounds.top, point.y);
      bounds.right = Math.max(bounds.right, point.x);
      bounds.bottom = Math.max(bounds.bottom, point.y);
    };
    var includeBox = function (matrix, left, top, right, bottom) {
      include(matrix, left, top);
      include(matrix, right, top);
      include(matrix, left, bottom);
      include(matrix, right, bottom);
    };
    var shapes = svg.querySelectorAll('path, rect, circle, ellipse, line, polyline, polygon, text, image');
    Array.prototype.forEach.call(shapes, function (shape) {
      if (shape.closest('defs, marker, clipPath, mask, pattern, symbol')) {
        return;
      }
      var local = shape.getScreenCTM();
      var box = shape.getBBox();
      if (!local || (!box.width && !box.height)) {
        return;
      }
      var matrix = toUser.multiply(local);
      var style = window.getComputedStyle(shape);
      var stroke = style.stroke && style.stroke !== 'none' ? (parseFloat(style.strokeWidth) || 0) / 2 : 0;
      includeBox(matrix, box.x - stroke, box.y - stroke, box.x + box.width + stroke, box.y + box.height + stroke);
      if (typeof shape.getTotalLength !== 'function') {
        return;
      }
      ['marker-start', 'marker-end'].forEach(function (property) {
        var reference = MARKER_REFERENCE.exec(shape.getAttribute(property) || style.getPropertyValue(property) || '');
        var marker = reference && document.getElementById(reference[1]);
        if (!marker) {
          return;
        }
        var size = Math.max(parseFloat(marker.getAttribute('markerWidth')) || 3,
            parseFloat(marker.getAttribute('markerHeight')) || 3);
        if (marker.getAttribute('markerUnits') !== 'userSpaceOnUse') {
          size *= parseFloat(style.strokeWidth) || 1;
        }
        var end = shape.getPointAtLength(property === 'marker-end' ? shape.getTotalLength() : 0);
        includeBox(matrix, end.x - size, end.y - size, end.x + size, end.y + size);
      });
    });
    return bounds;
  };
  var measureFit = function (figure) {
    var svg = figure.querySelector('.diagram-scroll svg');
    var bounds = null;
    try {
      bounds = svg ? drawnBounds(svg) : null;
    } catch (error) {
      bounds = null;
    }
    if (!bounds || bounds.right <= bounds.left || bounds.bottom <= bounds.top) {
      return null;
    }
    var left = Math.floor(bounds.left) - FIT_PADDING;
    var top = Math.floor(bounds.top) - FIT_PADDING;
    var width = Math.ceil(bounds.right) + FIT_PADDING - left;
    var height = Math.ceil(bounds.bottom) + FIT_PADDING - top;
    return { svg: svg, viewBox: left + ' ' + top + ' ' + width + ' ' + height, width: width, height: height };
  };
  var applyFit = function (figure, fit) {
    var svg = fit.svg;
    svg.setAttribute('viewBox', fit.viewBox);
    svg.setAttribute('preserveAspectRatio', 'xMidYMid meet');
    svg.setAttribute('width', String(fit.width));
    svg.setAttribute('height', String(fit.height));
    svg.style.removeProperty('max-width');
    svg.style.setProperty('--diagram-width', fit.width + 'px');
    figure.setAttribute('data-fitted', 'true');
  };
  var fitOnResize = function (figure) {
    var region = figure.querySelector('.diagram-scroll');
    if (!region || !window.ResizeObserver) {
      return;
    }
    var lastWidth = -1;
    var observer = new window.ResizeObserver(function () {
      var width = region.clientWidth;
      if (width === 0 || width === lastWidth) {
        return;
      }
      lastWidth = width;
      var fit = measureFit(figure);
      if (!fit || (figure.getAttribute('data-fitted') === 'true' && fit.svg.getAttribute('viewBox') === fit.viewBox)) {
        return;
      }
      figure.removeAttribute('data-fitted');
      window.requestAnimationFrame(function () {
        var next = measureFit(figure);
        if (next) {
          applyFit(figure, next);
        }
      });
    });
    observer.observe(region);
  };

  // Mermaid, on the diagram pages only: initialised once, strict, without HTML labels, and run only on the diagram
  // sources the generator emitted. Each drawing is then fitted. A failure leaves the source and shows a note; the
  // tables say the same.
  var diagrams = document.querySelectorAll('pre.diagram-source[data-diagram]');
  var mermaid = window.mermaid;
  if (diagrams.length > 0 && mermaid && typeof mermaid.initialize === 'function' && typeof mermaid.run === 'function') {
    mermaid.initialize({
      startOnLoad: false,
      securityLevel: 'strict',
      htmlLabels: false,
      flowchart: { htmlLabels: false, useMaxWidth: true },
      theme: 'base',
      fontFamily: 'system-ui, sans-serif',
      // The drawing sheet of docs/ui/documentation-site.md: ink lines on the light sheet in both themes. Node colours
      // come from the generated definitions and are not themed.
      themeVariables: {
        fontFamily: 'system-ui, sans-serif', fontSize: '18px',
        background: '#FFFDF7', edgeLabelBackground: '#FFFDF7',
        primaryColor: '#F4F1E8', primaryTextColor: '#172B3A',
        primaryBorderColor: '#687987', textColor: '#172B3A',
        lineColor: '#172B3A', clusterBkg: '#FFFDF7',
        clusterBorder: '#687987'
      }
    });
    mermaid.run({ nodes: Array.prototype.slice.call(diagrams) }).then(function () {
      Array.prototype.forEach.call(document.querySelectorAll('figure.diagram'), function (figure) {
        var fit = measureFit(figure);
        if (fit) {
          applyFit(figure, fit);
        }
        fitOnResize(figure);
      });
    }, function () {
      Array.prototype.forEach.call(document.querySelectorAll('.diagram-failed'), function (note) {
        note.hidden = false;
      });
    });
  }
})();
