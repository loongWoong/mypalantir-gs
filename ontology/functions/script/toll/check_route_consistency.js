function run(split_details, gantry_transactions) {
  if (split_details == null) split_details = [];
  if (gantry_transactions == null) gantry_transactions = [];
  var ids1 = [];
  for (var i = 0; i < split_details.length; i++) {
    var id = split_details[i].interval_id;
    if (id != null) ids1.push(String(id));
  }















  
  var ids2 = [];
  for (var j = 0; j < gantry_transactions.length; j++) {
    var id2 = gantry_transactions[j].toll_interval_id;
    if (id2 != null) ids2.push(String(id2));
  }
  ids1.sort();
  ids2.sort();
  if (ids1.length !== ids2.length) return false;
  for (var k = 0; k < ids1.length; k++) {
    if (ids1[k] !== ids2[k]) return false;
  }
  return true;
}
