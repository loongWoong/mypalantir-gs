function run(gantry_transactions) {
  if (gantry_transactions == null || !Array.isArray(gantry_transactions) || gantry_transactions.length <= 1) return true;
  var list = gantry_transactions.slice();
  list.sort(function (a, b) {
    var ta = a.receive_time != null ? (typeof a.receive_time === 'string' ? new Date(a.receive_time).getTime() : (a.receive_time.getTime ? a.receive_time.getTime() : Number(a.receive_time))) : 0;
    var tb = b.receive_time != null ? (typeof b.receive_time === 'string' ? new Date(b.receive_time).getTime() : (b.receive_time.getTime ? b.receive_time.getTime() : Number(b.receive_time))) : 0;
    return ta - tb;
  });
  for (var i = 0; i < list.length - 1; i++) {
    var after = list[i].balance_after;
    var nextBefore = list[i + 1].balance_before;
    if (after != null && nextBefore != null && Number(after) !== Number(nextBefore)) return false;
  }
  return true;
}
