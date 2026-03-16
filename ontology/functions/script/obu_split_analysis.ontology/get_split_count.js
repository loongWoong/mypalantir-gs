// 兼容 Java List：Nashorn 中 Java List 的 Array.isArray() 为 false，需用 length 判断
// 用 |0 确保返回 integer，避免 Nashorn 将 Java List.size() 提升为 double
function run(passage) {
  if (passage == null) return 0;
  var splitItems = passage.links != null ? passage.links.has_split_item : null;
  if (splitItems == null || typeof splitItems.length !== 'number') return 0;
  return splitItems.length | 0;
}
