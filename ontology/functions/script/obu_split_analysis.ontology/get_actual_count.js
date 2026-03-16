// 兼容 Java List：Nashorn 中 Java List 的 Array.isArray() 为 false
// Nashorn 中 Java List.size() 参与运算会被提升为 double，用 |0 强制返回 integer
function run(passage) {
  if (passage == null) return 0;
  var gantryTxs = passage.links != null ? passage.links.has_gantry_transaction : null;
  if (gantryTxs == null || typeof gantryTxs.length !== 'number') return 0;

  var count = 0;
  for (var i = 0; i < gantryTxs.length; i++) {
    var tollItems = gantryTxs[i].links != null ? gantryTxs[i].links.has_gantry_toll_item : null;
    if (tollItems != null && typeof tollItems.length === 'number') {
      count += tollItems.length | 0;
    }
  }
  return count | 0;
}
