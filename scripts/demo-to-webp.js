/*
  Converts docs/marketing/demo.gif, written by the screenshot renderer, into the animated WebP the
  README shows. runelite.net turns .gif images into plain links (to keep huge GIFs off the Plugin Hub
  pages), while a WebP stays an image; lossless keeps it pixel-exact and about a quarter of the size.

  Usage (needs Node.js and the sharp image library, installed outside the project):
    npm install --prefix %TEMP%\sharp-tool sharp
    set NODE_PATH=%TEMP%\sharp-tool\node_modules
    node scripts/demo-to-webp.js
*/
const path = require('path');
const sharp = require('sharp');

const dir = path.join(__dirname, '..', 'docs', 'marketing');

sharp(path.join(dir, 'demo.gif'), { animated: true })
	.webp({ lossless: true, effort: 6 })
	.toFile(path.join(dir, 'demo.webp'))
	.then(info => console.log(`demo.webp: ${info.size} bytes`));
