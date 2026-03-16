// 兼容 Java List：无 slice()，用循环复制；Array.isArray() 对 Java List 为 false
function run(split_items, gantry_transactions) {
  if (split_items == null || typeof split_items.length !== 'number') split_items = [];
  if (gantry_transactions == null || typeof gantry_transactions.length !== 'number') gantry_transactions = [];

  var splitIds = [];
  for (var i = 0; i < split_items.length; i++) {
    var id = split_items[i].toll_interval_id;
    if (id != null) splitIds.push(String(id));
  }

  var sortedTxs = [];
  for (var s = 0; s < gantry_transactions.length; s++) sortedTxs.push(gantry_transactions[s]);
  sortedTxs.sort(function (a, b) {
    var ta = a.trans_time != null ? (typeof a.trans_time === 'string' ? new Date(a.trans_time).getTime() : (a.trans_time.getTime ? a.trans_time.getTime() : Number(a.trans_time))) : 0;
    var tb = b.trans_time != null ? (typeof b.trans_time === 'string' ? new Date(b.trans_time).getTime() : (b.trans_time.getTime ? b.trans_time.getTime() : Number(b.trans_time))) : 0;
    return ta - tb;
  });

  var gantryIds = [];
  for (var j = 0; j < sortedTxs.length; j++) {
    var tollItems = sortedTxs[j].links != null ? sortedTxs[j].links.has_gantry_toll_item : null;
    if (tollItems == null || typeof tollItems.length !== 'number') continue;
    var items = [];
    for (var t = 0; t < tollItems.length; t++) items.push(tollItems[t]);
    items.sort(function (a, b) {
      return (Number(a.item_position) || 0) - (Number(b.item_position) || 0);
    });
    for (var k = 0; k < items.length; k++) {
      var gid = items[k].toll_interval_id;
      if (gid != null) gantryIds.push(String(gid));
    }
  }

  splitIds.sort();
  gantryIds.sort();

  if (splitIds.length !== gantryIds.length) return false;
  for (var m = 0; m < splitIds.length; m++) {
    if (splitIds[m] !== gantryIds[m]) return false;
  }
  return true;
}
