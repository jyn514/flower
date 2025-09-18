function run() {
  addFootnoteLine();
  addTimestamps();
  closeDraftPopup();
  expandDetails();

  let host;
  if (document.referrer) { host = (new URL(document.referrer)).host; }
  // if (host === "news.ycombinator.com" || host === "lobste.rs") {
  if (false) {
    let style = document.createElement('style');
    // let transform = host === "lobste.rs" ? 
    style.textContent = `
      body { text-transform: uppercase; }
      pre, code, blockquote { text-transform: none; }
    `;
    document.head.appendChild(style);
    console.log("HN readers clearly can't handle the typing habits of the average trans girl.");
    return;
  }
  console.info("hi there! thanks for visiting.");
  console.info("have a snake.");
  console.info("%c\n                 _           \n                | |          \n  ___ _ __   ___| | __       \n / __| '_ \\ / _ \\ |/ /       \n \\__ \\ | | |  __/   <        \n |___/_| |_|\\___|_|\\_\\       \n                             \n Web Development is no joke\u2122 \n                             ", 'background:#000;color:#0f0');
  console.info("(stolen with love from https://snek.dev/)");
}

function addFootnoteLine() {
  let footer = document.querySelector('footer.footnotes');
  if (footer === null) return;
  let hr = document.createElement('hr');
  footer.before(hr);
}

function addTimestamps() {
  const video = document.querySelector('video');
  if (!video) return;
  for (const elem of document.querySelectorAll(".timestamp")) {
    elem.onclick = function() {
      const stamp = elem.innerText;
      let [min, sec] = stamp.split(':').map(i => parseInt(i));
      sec += min*60;
      console.log(sec);
      video.currentTime = sec;
    };
  }
}

function closeDraftPopup() {
  const close = document.querySelector('aside.secret > button.close');
  if (!close) return;
  close.onclick = function() {
    close.parentElement.style.display = 'none';
  }
}

function expandDetails() {
  let expanded = false;
  let button = document.getElementById("expandAll");
  if (!button) return;
  let elems = document.querySelectorAll("details.note-content");
  button.onclick = function() {
    if (!expanded) {
      for (let detail of elems) {
        detail.setAttribute("open", "true");
      }
      button.textContent = button.textContent.replace('open', 'close');
      expanded = true;
    } else {
      for (let detail of elems) {
        detail.removeAttribute("open");
      }
      button.textContent = button.textContent.replace('close', 'open');
      expanded = false;
    }
  }
  button.click();
}

// The DOMContentLoaded event fires when the initial HTML
// document has been completely loaded and parsed, without
// waiting for stylesheets, images, and subframes to finish loading.
if (document.readyState !== 'loading') {
  run()
} else {
  document.addEventListener('DOMContentLoaded', (_event) => run());
}
