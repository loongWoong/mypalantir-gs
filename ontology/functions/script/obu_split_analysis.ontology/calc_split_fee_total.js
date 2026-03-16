// 兼容 Java List：Array.isArray() 对 Java List 为 false
function run(split_items) {
  if (split_items == null || typeof split_items.length !== 'number') return 0;
  var total = 0;
  for (var i = 0; i < split_items.length; i++) {
    var fee = split_items[i].fee;
    if (fee != null) total += Number(fee);
  }
  return total;
}
