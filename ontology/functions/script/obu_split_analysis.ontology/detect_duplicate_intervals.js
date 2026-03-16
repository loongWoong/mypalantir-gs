// 兼容 Java List：无 slice()，用循环复制；Array.isArray() 对 Java List 为 false
function run(gantry_transactions) {
  if (gantry_transactions == null || typeof gantry_transactions.length !== 'number') return false;

  var list = [];
  for (var s = 0; s < gantry_transactions.length; s++) list.push(gantry_transactions[s]);
  list.sort(function (a, b) {
    var ta = a.trans_time != null ? (typeof a.trans_time === 'string' ? new Date(a.trans_time).getTime() : (a.trans_time.getTime ? a.trans_time.getTime() : Number(a.trans_time))) : 0;
    var tb = b.trans_time != null ? (typeof b.trans_time === 'string' ? new Date(b.trans_time).getTime() : (b.trans_time.getTime ? b.trans_time.getTime() : Number(b.trans_time))) : 0;
    return ta - tb;
  });

  for (var i = 1; i < list.length; i++) {
    var currItems = list[i].links != null ? list[i].links.has_gantry_toll_item : null;
    var prevItems = list[i - 1].links != null ? list[i - 1].links.has_gantry_toll_item : null;
    if (currItems == null || prevItems == null || typeof currItems.length !== 'number' || typeof prevItems.length !== 'number') continue;

    var prevSet = {};
    for (var p = 0; p < prevItems.length; p++) {
      var pid = prevItems[p].toll_interval_id;
      if (pid != null) prevSet[String(pid)] = true;
    }
    for (var c = 0; c < currItems.length; c++) {
      var cid = currItems[c].toll_interval_id;
      if (cid != null && prevSet[String(cid)]) return true;
    }
  }
  return false;
}
