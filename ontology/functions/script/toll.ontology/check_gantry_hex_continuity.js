function run(gantry_transactions) {
  if (gantry_transactions == null) gantry_transactions = [];
  if (gantry_transactions.length <= 1) return true;
  for (var i = 0; i < gantry_transactions.length - 1; i++) {
    var curr = gantry_transactions[i];
    var next = gantry_transactions[i + 1];
    var currLast = curr != null ? curr.last_gantry_hex : null;
    var nextFirst = next != null ? next.gantry_hex : null;
    if (currLast == null || nextFirst == null) return false;
    if (String(currLast) !== String(nextFirst)) return false;
  }
  return true;
}
