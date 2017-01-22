var http = require('http');

http.createServer(function(request, response) {
	response.writeHead(200, {'Content-type' : 'text/plain'});

	response.end('Hello UofTHackers IV!\n');
}).listen(8000);

console.log("My server is running!");