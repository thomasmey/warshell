<%@ page language="java" contentType="text/html"%>
<!DOCTYPE html>
<html>
<head>
 <meta charset="utf-8" />
 <script src="https://cdn.jsdelivr.net/npm/xterm@4.2.0-vscode1/lib/xterm.min.js"></script>
 <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/xterm@4.2.0-vscode1/css/xterm.css"/>
 <title>WarShell</title>
</head>
<body>
	<%-- https://stackoverflow.com/questions/44447473/how-to-make-xterm-js-accept-input --%>
	<div id="xterm-container"></div>
	<script type="text/javascript">
		var socket = new WebSocket('ws://${pageContext.request.serverName}:${pageContext.request.serverPort}/warshell/ws');
		socket.binaryType = "arraybuffer";
		const term = new Terminal();
		term.open(document.getElementById('xterm-container'));

		socket.onmessage = function(event) {
			term.write(new Uint8Array(event.data));
		};
		term.onData(function(data) {
			socket.send(data);
		});
	</script>
</body>
</html>
