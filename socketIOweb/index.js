var app = require('express')();
var request = require('request'); // "Request" library
var http = require('http').Server(app);
var io = require('socket.io')(http);
var querystring = require('querystring');
var cookieParser = require('cookie-parser');
var util = require('util');
var Particle = require('particle-api-js');
var particle = new Particle();

var particle_token;

particle.login({username: '', password: ''}).then(
  function(data){
    console.log('API call completed on promise resolve: ', data.body.access_token);
    particle_token = data.body.access_token;
  },
  function(err) {
    console.log('API call completed on promise fail: ', err);
  }
);

var client_id = ''; // Your client id
var client_secret = ''; // Your secret
var redirect_uri = ''; // Your redirect uri

var playlist_id = '';
var userId = '0';
var access_token;
var refresh_token;

var track_ids = [];
var track_bpm = [];
var track_energy = [];
var track_names = [];
var track_img_url = [];
var track_live = [];
var track_dance = [];
var score = [];
var num_tracks;
var last_sent_idx;

app.get('/', function(req, res){
  res.sendFile(__dirname + '/index.html');
});

io.on('connection', function(socket){
  console.log('a user connected');
  socket.on('new message', function(msg){
    io.emit('new message', msg);
  });
});


/**
 * Generates a random string containing numbers and letters
 * @param  {number} length The length of the string
 * @return {string} The generated string
 */
var generateRandomString = function(length) {
  var text = '';
  var possible = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';

  for (var i = 0; i < length; i++) {
    text += possible.charAt(Math.floor(Math.random() * possible.length));
  }
  return text;
};

var stateKey = 'spotify_auth_state';

app.use(cookieParser());

app.get('/login', function(req, res) {
  var state = generateRandomString(16);
  res.cookie(stateKey, state);

  // your application requests authorization
  var scope = 'user-read-private user-read-email';
  res.redirect('https://accounts.spotify.com/authorize?' +
    querystring.stringify({
      response_type: 'code',
      client_id: client_id,
      scope: scope,
      redirect_uri: redirect_uri,
      state: state
    }));
});

app.get('/playlist', function(req, res) {
  var q = {
    url: 'https://api.spotify.com/v1/users/' + userId + '/playlists/' + playlist_id + '/tracks',
    headers: { 'Authorization': 'Bearer ' + access_token },
    json: true
  };

  request.get(q, function(error, response, body) {
    if(!error && response.statusCode === 200 ) {
      num_tracks = body.items.length;
      var i;
      for(i = 0; i < num_tracks; i++) {
        track_ids.push(body.items[i].track.id);
        track_names.push(body.items[i].track.name);
        track_img_url.push(body.items[i].track.album.images[1].url);
      }
      console.log(track_ids);
      console.log(track_names);
      console.log(track_img_url);
    }
  });
});

app.get('/getBPM', function(req, res) {
  var i;
  var qArg = '?ids=';
  for(i = 0; i < num_tracks; i++) {
    qArg += (track_ids[i] + (i != (num_tracks - 1) ? ',' : ''));
  }

  q2 = {
    url: 'https://api.spotify.com/v1/audio-features/' + qArg,
    headers: { 'Authorization': 'Bearer ' + access_token },
    json:true
  };
  request.get(q2, function(error, response, body) {
    for(i = 0; i < num_tracks; i++) {
      track_bpm[i] = body.audio_features[i].tempo;
      track_energy[i] = body.audio_features[i].energy;
      track_live[i] = body.audio_features[i].liveness;
      track_dance[i] = body.audio_features[i].danceability;
      score[i] = (track_dance[i] + 0.5*track_energy[i])*track_bpm[i];
    }
    console.log(track_bpm);
    console.log(track_energy);
    console.log(track_live);
    console.log(track_dance);
    console.log(score);
  });
});

var idx = 0;
app.get('/sendsong', function(req, res) {

  particle.getVariable({ deviceId: 'jerry-uofthacks', name: 'bpm', auth: particle_token }).then(function(data) {
    console.log('Device variable retrieved successfully:', data);

    var target_score = data.body.result * 1.22;
    console.log('target score: ', target_score);

    //score is (danceability + 0.5*energy ) * bpm
    //compared to 2 * person's bpm
    var i;
    var best_match_track_id = 0;
    var best_match_score = 100000;
    for(i = 0; i < num_tracks; i++) {
      if(Math.abs(score[i]- target_score) < best_match_score && i != last_sent_idx) {
        best_match_track_id = i;
        best_match_score = Math.abs(score[i] - target_score);
      }
    }
    last_sent_idx = best_match_track_id;
    io.emit('new message',
      {id: track_ids[best_match_track_id],
        name: track_names[best_match_track_id],
        bpm: track_bpm[best_match_track_id],
        img_url: track_img_url[best_match_track_id]
      });
  }, function(err) {
    console.log('An error occurred while getting attrs:', err);
  });

  /*io.emit('new message',
    {id: track_ids[idx],
      name: track_names[idx],
      bpm: track_bpm[idx],
      img_url: track_img_url[idx]
    });
  ++idx;*/
});


app.get('/callback', function(req, res) {

  // your application requests refresh and access tokens
  // after checking the state parameter

  var code = req.query.code || null;
  var state = req.query.state || null;
  var storedState = req.cookies ? req.cookies[stateKey] : null;

  if (state === null || state !== storedState) {
    res.redirect('/#' +
      querystring.stringify({
        error: 'state_mismatch'
      }));
  } else {
    res.clearCookie(stateKey);
    var authOptions = {
      url: 'https://accounts.spotify.com/api/token',
      form: {
        code: code,
        redirect_uri: redirect_uri,
        grant_type: 'authorization_code'
      },
      headers: {
        'Authorization': 'Basic ' + (new Buffer(client_id + ':' + client_secret).toString('base64'))
      },
      json: true
    };

    request.post(authOptions, function(error, response, body) {
      if (!error && response.statusCode === 200) {

        access_token = body.access_token;
        refresh_token = body.refresh_token;

        var options = {
          url: 'https://api.spotify.com/v1/me',
          headers: { 'Authorization': 'Bearer ' + access_token },
          json: true
        };

        // use the access token to access the Spotify Web API
        request.get(options, function(error, response, body) {
          console.log(body);
          userId = body.id;
          console.log(userId);
        });

        // we can also pass the token to the browser to make requests from there
        res.redirect('/#' +
          querystring.stringify({
            access_token: access_token,
            refresh_token: refresh_token
          }));
      } else {
        res.redirect('/#' +
          querystring.stringify({
            error: 'invalid_token'
          }));
      }
    });
  }
});

http.listen(8888, function(){
  console.log('listening on *:8888');
});
