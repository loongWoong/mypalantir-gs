// 兼容 Java List：Array.isArray() 对 Java List 为 false
function run(gantry_transactions, split_time) {
  if (gantry_transactions == null || typeof gantry_transactions.length !== 'number') return false;
  if (split_time == null) return false;

  var st = typeof split_time === 'string'
    ? new Date(split_time).getTime()
    : (split_time.getTime ? split_time.getTime() : Number(split_time));

  for (var i = 0; i < gantry_transactions.length; i++) {
    var rt = gantry_transactions[i].receive_time;
    if (rt == null) continue;
    var rtMs = typeof rt === 'string'
      ? new Date(rt).getTime()
      : (rt.getTime ? rt.getTime() : Number(rt));
    if (rtMs > st) return true;
  }
  return false;
}
