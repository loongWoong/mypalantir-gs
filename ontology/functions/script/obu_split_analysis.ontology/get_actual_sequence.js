// 兼容 Java List：无 slice()，用循环复制；Array.isArray() 对 Java List 为 false
function run(passage) {
  if (passage == null) return [];
  var gantryTxs = passage.links != null ? passage.links.has_gantry_transaction : null;
  if (gantryTxs == null || typeof gantryTxs.length !== 'number') return [];

  var list = [];
  for (var i = 0; i < gantryTxs.length; i++) list.push(gantryTxs[i]);
  list.sort(function (a, b) {
    var ta = a.trans_time != null ? (typeof a.trans_time === 'string' ? new Date(a.trans_time).getTime() : (a.trans_time.getTime ? a.trans_time.getTime() : Number(a.trans_time))) : 0;
    var tb = b.trans_time != null ? (typeof b.trans_time === 'string' ? new Date(b.trans_time).getTime() : (b.trans_time.getTime ? b.trans_time.getTime() : Number(b.trans_time))) : 0;
    return ta - tb;
  });

  var result = [];
  for (var i = 0; i < list.length; i++) {
    var tx = list[i];
    var tollItems = tx.links != null ? tx.links.has_gantry_toll_item : null;
    if (tollItems == null || typeof tollItems.length !== 'number') continue;

    var items = [];
    for (var k = 0; k < tollItems.length; k++) items.push(tollItems[k]);
    items.sort(function (a, b) {
      return (Number(a.item_position) || 0) - (Number(b.item_position) || 0);
    });
    for (var j = 0; j < items.length; j++) {
      var id = items[j].toll_interval_id;
      if (id != null) result.push(String(id));
    }
  }
  return result;
}
