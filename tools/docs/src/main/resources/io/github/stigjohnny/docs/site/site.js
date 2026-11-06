/*
 * The site's one script, generated with the site by tools/docs: the theme switch, the navigation disclosure, the copy
 * button of the brain, diagram enlargement, and Mermaid on the diagram pages. It stores nothing, fetches nothing and
 * writes no markup. Without it every page still works: the navigation stays open and the diagrams show their source.
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
  // usable: a focused navigation that collapses hands focus to its summary, and a focused summary that disappears as
  // the navigation opens hands it to the current page's link, or the first link.
  var disclosure = document.querySelector('.site-nav-disclosure');
  if (disclosure && window.matchMedia) {
    var wide = window.matchMedia('(min-width: 720px)');
    var summary = disclosure.querySelector('summary');
    var syncNavigation = function () {
      var focusInside = disclosure.contains(document.activeElement);
      var summaryFocused = summary !== null && document.activeElement === summary;
      var opening = wide.matches;
      if (disclosure.open === opening) {
        return;
      }
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

  // Diagram enlargement: a toggle button (aria-pressed); the enlarged diagram scrolls inside its labelled region.
  Array.prototype.forEach.call(document.querySelectorAll('[data-enlarge]'), function (button) {
    var figure = document.getElementById(button.getAttribute('data-enlarge'));
    if (!figure) {
      return;
    }
    var fitted = null;
    button.hidden = false;
    button.addEventListener('click', function () {
      var enlarged = button.getAttribute('aria-pressed') !== 'true';
      var svg = figure.querySelector('svg');
      var region = figure.querySelector('.diagram-scroll');
      button.setAttribute('aria-pressed', enlarged ? 'true' : 'false');
      figure.classList.toggle('is-enlarged', enlarged);
      if (!svg || !region) {
        return;
      }
      if (fitted === null) {
        fitted = { width: svg.style.width, maxWidth: svg.style.maxWidth };
      }
      var box = svg.viewBox && svg.viewBox.baseVal;
      if (enlarged && box && box.width) {
        svg.style.width = Math.round(Math.max(box.width, region.clientWidth * 1.5)) + 'px';
        svg.style.maxWidth = 'none';
      } else {
        svg.style.width = fitted.width;
        svg.style.maxWidth = fitted.maxWidth;
      }
    });
  });

  // Mermaid, on the diagram pages only: initialised once, strict, without HTML labels, and run only on the diagram
  // sources the generator emitted. A failure leaves the source and shows a note; the tables say the same.
  var diagrams = document.querySelectorAll('pre.diagram-source[data-diagram]');
  var mermaid = window.mermaid;
  if (diagrams.length > 0 && mermaid && typeof mermaid.initialize === 'function' && typeof mermaid.run === 'function') {
    mermaid.initialize({
      startOnLoad: false,
      securityLevel: 'strict',
      htmlLabels: false,
      flowchart: { htmlLabels: false, useMaxWidth: true },
      theme: 'default',
      fontFamily: 'system-ui, sans-serif',
      themeVariables: { fontFamily: 'system-ui, sans-serif', fontSize: '16px' }
    });
    mermaid.run({ nodes: Array.prototype.slice.call(diagrams) }).catch(function () {
      Array.prototype.forEach.call(document.querySelectorAll('.diagram-failed'), function (note) {
        note.hidden = false;
      });
    });
  }
})();
