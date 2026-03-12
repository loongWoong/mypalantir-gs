function run(split_details, gantry_transactions) {
  if (split_details == null || !Array.isArray(split_details) || gantry_transactions == null || !Array.isArray(gantry_transactions)) return false;
  var listA = [];
  for (var i = 0; i < gantry_transactions.length; i++) {
    var s = gantry_transactions[i].fee_group;
    if (s == null) continue;
    var parts = String(s).split('|');
    for (var j = 0; j < parts.length; j++) {
      var n = parseFloat(parts[j]);
      if (!isNaN(n)) listA.push(n);
    }
  }
  var listB = [];
  for (var k = 0; k < split_details.length; k++) {
    var fee = split_details[k].toll_interval_fee;
    if (fee != null) listB.push(Number(fee));
  }
  listA.sort(function (a, b) { return a - b; });
  listB.sort(function (a, b) { return a - b; });
  if (listA.length !== listB.length) return false;
  for (var m = 0; m < listA.length; m++) {
    if (listA[m] !== listB[m]) return false;
  }
  return true;
}
