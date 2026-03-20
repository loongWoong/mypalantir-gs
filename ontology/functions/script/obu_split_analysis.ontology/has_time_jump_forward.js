// 判断物理路径连续的门架之间是否存在时间间隔过短（跳时）
// 物理路径连续：txs[i].lastGantryHex == txs[i-1].gantryHex
// 时间间隔过短：gap < min_gap_minutes * 60 秒
function run(gantry_transactions, min_gap_minutes) {
  if (gantry_transactions == null || typeof gantry_transactions.length !== 'number') return false;
  if (min_gap_minutes == null) min_gap_minutes = 1;
  var thresholdMs = min_gap_minutes * 60 * 1000;

  var list = [];
  for (var s = 0; s < gantry_transactions.length; s++) list.push(gantry_transactions[s]);
  list.sort(function (a, b) {
    var ta = a.trans_time != null ? (typeof a.trans_time === 'string' ? new Date(a.trans_time).getTime() : (a.trans_time.getTime ? a.trans_time.getTime() : Number(a.trans_time))) : 0;
    var tb = b.trans_time != null ? (typeof b.trans_time === 'string' ? new Date(b.trans_time).getTime() : (b.trans_time.getTime ? b.trans_time.getTime() : Number(b.trans_time))) : 0;
    return ta - tb;
  });

  for (var i = 1; i < list.length; i++) {
    var lastHex = list[i].last_gantry_hex;
    var prevHex = list[i - 1].gantry_hex;
    if (lastHex == null || prevHex == null) continue;
    if (String(lastHex) !== String(prevHex)) continue; // 非物理路径连续

    var tCurr = list[i].trans_time;
    var tPrev = list[i - 1].trans_time;
    if (tCurr == null || tPrev == null) continue;
    var currMs = typeof tCurr === 'string' ? new Date(tCurr).getTime() : (tCurr.getTime ? tCurr.getTime() : Number(tCurr));
    var prevMs = typeof tPrev === 'string' ? new Date(tPrev).getTime() : (tPrev.getTime ? tPrev.getTime() : Number(tPrev));
    var gap = Math.abs(currMs - prevMs);
    if (gap < thresholdMs) return true; // 时间间隔过短
  }
  return false;
}
