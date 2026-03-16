// 兼容 Java List：无 slice()，用循环复制；Array.isArray() 对 Java List 为 false
function run(passage) {
  if (passage == null) return [];
  var splitItems = passage.links != null ? passage.links.has_split_item : null;
  if (splitItems == null || typeof splitItems.length !== 'number') return [];

  var list = [];
  for (var i = 0; i < splitItems.length; i++) list.push(splitItems[i]);
  list.sort(function (a, b) {
    return (Number(a.position) || 0) - (Number(b.position) || 0);
  });

  var result = [];
  for (var i = 0; i < list.length; i++) {
    var id = list[i].toll_interval_id;
    if (id != null) result.push(String(id));
  }
  return result;
}
