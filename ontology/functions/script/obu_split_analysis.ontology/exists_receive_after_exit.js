// 判断是否存在门架交易接收时间晚于出口时间
function run(gantry_transactions, exit_time) {
  if (gantry_transactions == null || typeof gantry_transactions.length !== 'number') return false;
  if (exit_time == null) return false;

  var et = typeof exit_time === 'string'
    ? new Date(exit_time).getTime()
    : (exit_time.getTime ? exit_time.getTime() : Number(exit_time));

  for (var i = 0; i < gantry_transactions.length; i++) {
    var rt = gantry_transactions[i].receive_time;
    if (rt == null) continue;
    var rtMs = typeof rt === 'string'
      ? new Date(rt).getTime()
      : (rt.getTime ? rt.getTime() : Number(rt));
    if (rtMs > et) return true;
  }
  return false;
}
