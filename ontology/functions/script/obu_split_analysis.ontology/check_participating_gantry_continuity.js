// 兼容 Java List：Array.isArray() 对 Java List 为 false
function run(gantry_transactions) {
  if (gantry_transactions == null || typeof gantry_transactions.length !== 'number') return true;

  var participating = [];
  for (var i = 0; i < gantry_transactions.length; i++) {
    var flag = gantry_transactions[i].in_split_flag;
    if (flag === 1 || flag === '1') {
      participating.push(gantry_transactions[i]);
    }
  }

  if (participating.length <= 1) return true;

  participating.sort(function (a, b) {
    var ta = a.trans_time != null ? (typeof a.trans_time === 'string' ? new Date(a.trans_time).getTime() : (a.trans_time.getTime ? a.trans_time.getTime() : Number(a.trans_time))) : 0;
    var tb = b.trans_time != null ? (typeof b.trans_time === 'string' ? new Date(b.trans_time).getTime() : (b.trans_time.getTime ? b.trans_time.getTime() : Number(b.trans_time))) : 0;
    return ta - tb;
  });

  for (var j = 1; j < participating.length; j++) {
    var lastHex = participating[j].last_gantry_hex;
    var prevHex = participating[j - 1].gantry_hex;
    if (lastHex == null || prevHex == null) return false;
    if (String(lastHex) !== String(prevHex)) return false;
  }
  return true;
}
