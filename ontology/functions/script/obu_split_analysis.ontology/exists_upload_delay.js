// 兼容 Java List：Array.isArray() 对 Java List 为 false
function run(gantry_transactions, threshold_days) {
  if (gantry_transactions == null || typeof gantry_transactions.length !== 'number') return false;
  var days = threshold_days != null ? Number(threshold_days) : 3;
  var thresholdMs = days * 24 * 60 * 60 * 1000;

  for (var i = 0; i < gantry_transactions.length; i++) {
    var tx = gantry_transactions[i];
    var rt = tx.receive_time;
    var tt = tx.trans_time;
    if (rt == null || tt == null) continue;

    var rtMs = typeof rt === 'string'
      ? new Date(rt).getTime()
      : (rt.getTime ? rt.getTime() : Number(rt));
    var ttMs = typeof tt === 'string'
      ? new Date(tt).getTime()
      : (tt.getTime ? tt.getTime() : Number(tt));

    if ((rtMs - ttMs) > thresholdMs) return true;
  }
  return false;
}
