// assembly/full_mosaic.ts
// Ful KD-tree + tile matching in AssemblyScript.
// Exports simple setters so JS can upload the kd-tree and tiles.
// Uses squared distance (no sqrt) for comparisons for determinism & speed.

let nodeCount: i32 = 0;
let nodeAvg: StaticArray<f32> = new StaticArray<f32>(0); // length = nodeCount * 3
let nodeLeft: StaticArray<i32> = new StaticArray<i32>(0);
let nodeRight: StaticArray<i32> = new StaticArray<i32>(0);
let nodeAxis: StaticArray<i32> = new StaticArray<i32>(0);
let nodeSrcIndex: StaticArray<i32> = new StaticArray<i32>(0); // index into JS emoji list

let rootIndex: i32 = -1;

// Tiles
let tileCount: i32 = 0;
let tileData: StaticArray<f32> = new StaticArray<f32>(0); // length = tileCount * 3
let results: StaticArray<i32> = new StaticArray<i32>(0);

// Temporary best for recursion
let tmpBestDist: f32 = 0.0;
let tmpBestSrc: i32 = -1;

// Initialize node storage
export function initNodeCount(count: i32): void {
  nodeCount = count;
  nodeAvg = new StaticArray<f32>(count * 3);
  nodeLeft = new StaticArray<i32>(count);
  nodeRight = new StaticArray<i32>(count);
  nodeAxis = new StaticArray<i32>(count);
  nodeSrcIndex = new StaticArray<i32>(count);
  // fill defaults
  for (let i: i32 = 0; i < count; i++) {
    unchecked(nodeLeft[i] = -1);
    unchecked(nodeRight[i] = -1);
    unchecked(nodeAxis[i] = 0);
    unchecked(nodeSrcIndex[i] = -1);
    let off = i * 3;
    unchecked(nodeAvg[off] = 0.0);
    unchecked(nodeAvg[off + 1] = 0.0);
    unchecked(nodeAvg[off + 2] = 0.0);
  }
}

export function setNodeAt(
  index: i32,
  r: f32, g: f32, b: f32,
  left: i32, right: i32,
  axis: i32,
  srcIndex: i32
): void {
  let off = index * 3;
  unchecked(nodeAvg[off] = r);
  unchecked(nodeAvg[off + 1] = g);
  unchecked(nodeAvg[off + 2] = b);
  unchecked(nodeLeft[index] = left);
  unchecked(nodeRight[index] = right);
  unchecked(nodeAxis[index] = axis);
  unchecked(nodeSrcIndex[index] = srcIndex);
}

export function setRootIndex(idx: i32): void {
  rootIndex = idx;
}

// Tiles: initialize and set per tile
export function initTileCount(count: i32): void {
  tileCount = count;
  tileData = new StaticArray<f32>(count * 3);
  results = new StaticArray<i32>(count);
  for (let i: i32 = 0; i < count; i++) {
    let off = i * 3;
    unchecked(tileData[off] = 0.0);
    unchecked(tileData[off + 1] = 0.0);
    unchecked(tileData[off + 2] = 0.0);
    unchecked(results[i] = -1);
  }
}

export function setTileColorAt(index: i32, r: f32, g: f32, b: f32): void {
  let off = index * 3;
  unchecked(tileData[off] = r);
  unchecked(tileData[off + 1] = g);
  unchecked(tileData[off + 2] = b);
}

// squared distance inline
@inline
function distSq(r1: f32, g1: f32, b1: f32, r2: f32, g2: f32, b2: f32): f32 {
  let dr = r1 - r2;
  let dg = g1 - g2;
  let db = b1 - b2;
  return dr * dr + dg * dg + db * db;
}

// Recursive KD-tree search that updates tmpBestDist and tmpBestSrc
function searchNode(nodeIdx: i32, tr: f32, tg: f32, tb: f32): void {
  if (nodeIdx < 0) return;
  let off = nodeIdx * 3;
  let nr = unchecked(nodeAvg[off]);
  let ng = unchecked(nodeAvg[off + 1]);
  let nb = unchecked(nodeAvg[off + 2]);

  // check current node
  let d = distSq(nr, ng, nb, tr, tg, tb);
  // deterministic tie-break: smaller srcIndex wins if distances equal
  let src = unchecked(nodeSrcIndex[nodeIdx]);
  if (d < tmpBestDist || (d == tmpBestDist && src >= 0 && tmpBestSrc >= 0 && src < tmpBestSrc)) {
    tmpBestDist = d;
    tmpBestSrc = src;
  } else if (d < tmpBestDist && tmpBestSrc < 0) {
    tmpBestDist = d;
    tmpBestSrc = src;
  } else if (tmpBestSrc < 0 && src >= 0) {
    // if tmpBestSrc was not set yet
    tmpBestDist = d;
    tmpBestSrc = src;
  }

  // decide which subtree first
  let axis = unchecked(nodeAxis[nodeIdx]);
  // value at axis
  let diff: f32 = 0.0;
  if (axis == 0) diff = tr - nr;
  else if (axis == 1) diff = tg - ng;
  else diff = tb - nb;

  let first = diff <= 0 ? unchecked(nodeLeft[nodeIdx]) : unchecked(nodeRight[nodeIdx]);
  let second = first == unchecked(nodeLeft[nodeIdx]) ? unchecked(nodeRight[nodeIdx]) : unchecked(nodeLeft[nodeIdx]);

  if (first >= 0) searchNode(first, tr, tg, tb);
  let diffSqVal = diff * diff;
  if (diffSqVal <= tmpBestDist && second >= 0) searchNode(second, tr, tg, tb);
}

// Process all tiles and populate results[]
export function processAll(): void {
  if (rootIndex < 0) {
    // no tree defined: mark results as -1
    for (let i: i32 = 0; i < tileCount; i++) {
      unchecked(results[i] = -1);
    }
    return;
  }
  for (let i: i32 = 0; i < tileCount; i++) {
    let off = i * 3;
    let tr = unchecked(tileData[off]);
    let tg = unchecked(tileData[off + 1]);
    let tb = unchecked(tileData[off + 2]);
    tmpBestDist = 1e30;
    tmpBestSrc = -1;
    searchNode(rootIndex, tr, tg, tb);
    unchecked(results[i] = tmpBestSrc);
  }
}

// read back results: returns srcIndex (index into JS emoji list) or -1
export function getResultAt(index: i32): i32 {
  return unchecked(results[index]);
}
