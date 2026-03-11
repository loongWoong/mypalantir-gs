function run(exitTransaction) {
  if (exitTransaction == null) return false;
  var actual = exitTransaction.actual_fee_class;
  return actual === 1 || actual === '1';
}
