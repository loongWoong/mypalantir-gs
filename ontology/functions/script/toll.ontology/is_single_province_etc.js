function run(exitTransaction) {
  if (exitTransaction == null) return false;
  var multi = exitTransaction.multi_province;
  var pay = exitTransaction.pay_type;
  return (multi === 0 || multi === '0') && (pay === 4 || pay === '4');
}
