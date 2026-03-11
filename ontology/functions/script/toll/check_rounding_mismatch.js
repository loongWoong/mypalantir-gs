function run(gantry_transactions, tolerance) {
  if (gantry_transactions == null || !Array.isArray(gantry_transactions)) return false;
  var ratio = tolerance != null ? Number(tolerance) : 0.95;
  var sumPayFee = 0, sumFee = 0;
  for (var i = 0; i < gantry_transactions.length; i++) {
    sumPayFee += Number(gantry_transactions[i].pay_fee) || 0;
    sumFee += Number(gantry_transactions[i].fee) || 0;
  }
  var rounded = Math.round(sumPayFee);
  return rounded * ratio !== sumFee;
}
