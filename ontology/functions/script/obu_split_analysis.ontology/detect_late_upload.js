// 兼容 Java List：Array.isArray() 对 Java List 为 false
function run(gantry_transactions, split_time) {
  if (gantry_transactions == null || typeof gantry_transactions.length !== 'number') return false;
  if (split_time == null) return false;

  function toMillis(x) {
    if (x == null) return NaN;
    if (typeof x === 'number') return x;
    if (typeof x === 'string') return new Date(x).getTime();
    if (x.getTime) return x.getTime(); // JS Date / java.util.Date / Timestamp 等
    // 兼容 java.time.LocalDateTime 等：优先用 toString() 后再解析
    try {
      var s = String(x);
      var ms = new Date(s).getTime();
      if (!isNaN(ms)) return ms;
    } catch (e) {}
    return Number(x);
  }

  var st = toMillis(split_time);
  if (isNaN(st)) return false;

  for (var i = 0; i < gantry_transactions.length; i++) {
    var rt = gantry_transactions[i].receive_time;
    if (rt == null) continue;
    var rtMs = toMillis(rt);
    if (isNaN(rtMs)) continue;
    if (rtMs > st) return true;
  }
  return false;
}
