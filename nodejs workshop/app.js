var express = require('express');
var app = express();
var bodyParser = require('body-parser');

app.use(bodyParser.urlencoded({extended: true}));
app.use(bodyParser.json());

//set our port number
var port = 8000;

var router = express.Router();
router.get('/api/test', function(req, res) {
  res.json({
    message: "This api actually works!"
  });
});

router.get('/var/:variable', function(req, res) {
  var data = req.params.variable;

  res.json({
    var: data
  });
});

app.use('/', router);

app.listen(port);
console.log("Listening on port " + port);
