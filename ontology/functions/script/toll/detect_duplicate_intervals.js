function run(gantry_transactions) {
  if (gantry_transactions == null) gantry_transactions = [];
  var seen = {};
  for (var i = 0; i < gantry_transactions.length; i++) {
    var id = gantry_transactions[i].toll_interval_id;
    if (id == null) continue;
    var key = String(id);
    if (seen[key]) return true;
    seen[key] = true;
  }
  return false;
}
