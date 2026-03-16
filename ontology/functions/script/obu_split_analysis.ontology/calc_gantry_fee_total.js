// 兼容 Java List：Array.isArray() 对 Java List 为 false
function run(gantry_transactions) {
  if (gantry_transactions == null || typeof gantry_transactions.length !== 'number') return 0;

  var total = 0;
  for (var i = 0; i < gantry_transactions.length; i++) {
    var g = gantry_transactions[i];
    var tollItems = g.links != null ? g.links.has_gantry_toll_item : null;
    if (tollItems != null && typeof tollItems.length === 'number') {
      for (var j = 0; j < tollItems.length; j++) {
        var fee = tollItems[j].fee;
        if (fee != null) {
          total += Number(fee);
        }
      }
    } else if (g.fee_group != null && String(g.fee_group).length > 0) {
      var parts = String(g.fee_group).split("|");
      for (var k = 0; k < parts.length; k++) {
        var v = parseFloat(parts[k]);
        if (!isNaN(v)) total += v;
      }
    }
  }
  return total;
}
