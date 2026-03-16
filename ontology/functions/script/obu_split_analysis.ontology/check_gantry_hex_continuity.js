// 兼容 Java List：无 slice()，用循环复制；Array.isArray() 对 Java List 为 false
function run(gantry_transactions) {
  if (gantry_transactions == null || typeof gantry_transactions.length !== 'number') return true;
  if (gantry_transactions.length <= 1) return true;

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
    if (lastHex == null || prevHex == null) return false;
    if (String(lastHex) !== String(prevHex)) return false;
  }
  return true;
}
