// 兼容 Java List：Array.isArray() 对 Java List 为 false
function run(split_items, gantry_transactions) {
  if (split_items == null || typeof split_items.length !== 'number') return false;
  if (gantry_transactions == null || typeof gantry_transactions.length !== 'number') return false;

  var splitByUnit = {};
  for (var i = 0; i < split_items.length; i++) {
    var uid = split_items[i].toll_interval_id;
    var fee = split_items[i].fee;
    if (uid == null) continue;
    var key = String(uid);
    splitByUnit[key] = (splitByUnit[key] || 0) + Number(fee);
  }

  var gantryByUnit = {};
  for (var j = 0; j < gantry_transactions.length; j++) {
    var tollItems = gantry_transactions[j].links != null ? gantry_transactions[j].links.has_gantry_toll_item : null;
    if (tollItems == null || typeof tollItems.length !== 'number') continue;
    for (var k = 0; k < tollItems.length; k++) {
      var gUid = tollItems[k].toll_interval_id;
      var gFee = tollItems[k].fee;
      if (gUid == null) continue;
      var gKey = String(gUid);
      gantryByUnit[gKey] = (gantryByUnit[gKey] || 0) + Number(gFee);
    }
  }

  var splitKeys = Object.keys(splitByUnit).sort();
  var gantryKeys = Object.keys(gantryByUnit).sort();

  if (splitKeys.length !== gantryKeys.length) return false;
  for (var m = 0; m < splitKeys.length; m++) {
    if (splitKeys[m] !== gantryKeys[m]) return false;
    if (splitByUnit[splitKeys[m]] !== gantryByUnit[gantryKeys[m]]) return false;
  }
  return true;
}
