const express = require('express');
const cors = require('cors');
const http = require('http');
const { Server } = require('socket.io');

// NEW: Import native WebSockets and Yjs routing utility
const WebSocket = require('ws');
const { setupWSConnection } = require('y-websocket/bin/utils');
require('dotenv').config();

const app = express();
const PORT = process.env.PORT || 5000;

app.use(cors());
app.use(express.json());

app.get('/', (req, res) => {
  res.send('Disco Code Server is running. Try /status for health check.');
});
app.get('/status', (req, res) => {
  res.status(200).json({ status: 'Node Server Running' });
});

const server = http.createServer(app);

// --- PART 1: SOCKET.IO (Handles Chat & Presence) ---
const io = new Server(server, {
  cors: {
    origin: true, // Automatically reflects request origin (allows localhost, Vercel, etc.)
    methods: ['GET', 'POST'],
    credentials: true,
  },
});

const getRoomUserCount = (roomId) => {
  const room = io.sockets.adapter.rooms.get(roomId);
  return room ? room.size : 0;
};

const broadcastRoomUserCount = (roomId) => {
  const count = getRoomUserCount(roomId);
  io.to(roomId).emit('room-users-update', { roomId, count });
  console.log(`Room ${roomId} now has ${count} user(s)`);
};

io.on('connection', (socket) => {
  console.log('User connected to Chat/Presence:', socket.id);

  socket.on('join-room', (roomId) => {
    socket.join(roomId);
    socket.currentRoom = roomId;
    console.log(`Socket ${socket.id} joined room: ${roomId}`);
    broadcastRoomUserCount(roomId);
  });

  socket.on('leave-room', (roomId) => {
    socket.leave(roomId);
    console.log(`Socket ${socket.id} left room: ${roomId}`);
    broadcastRoomUserCount(roomId);
  });

  socket.on('get-rooms-info', (roomIds) => {
    if (!Array.isArray(roomIds)) return;
    const counts = {};
    roomIds.forEach((roomId) => {
      counts[roomId] = getRoomUserCount(roomId);
    });
    socket.emit('rooms-info', counts);
  });

  socket.on('send-message', (messageData) => {
    console.log('Message received:', messageData);
    socket.to(messageData.roomId).emit('receive-message', messageData);
  });

  // REMOVED: 'code-change' and 'cursor-change' are now handled entirely by Yjs.

  socket.on('disconnect', () => {
    console.log('User disconnected:', socket.id);
    if (socket.currentRoom) {
      broadcastRoomUserCount(socket.currentRoom);
    }
  });
});

// --- PART 2: YJS WEBSOCKETS (Handles Code, Cursors, & Undo) ---

// 1. Create a native WebSocket server attached to the HTTP server
const wss = new WebSocket.Server({ noServer: true });

// 2. Let y-websocket handle the background document synchronization
wss.on('connection', setupWSConnection);

// 3. Traffic cop: Route incoming connections to the right engine
server.on('upgrade', (request, socket, head) => {
  // If the request is for our chat (Socket.io), let Socket.io handle it
  if (request.url.startsWith('/socket.io')) {
    return;
  }
  
  // Otherwise, route the connection to the Yjs server for code syncing
  wss.handleUpgrade(request, socket, head, (ws) => {
    wss.emit('connection', ws, request);
  });
});

server.listen(PORT, () => {
  console.log(`Server running on port ${PORT}`);
});