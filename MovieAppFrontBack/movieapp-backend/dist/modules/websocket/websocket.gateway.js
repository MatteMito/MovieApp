"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
var __metadata = (this && this.__metadata) || function (k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function") return Reflect.metadata(k, v);
};
var WebsocketGateway_1;
Object.defineProperty(exports, "__esModule", { value: true });
exports.WebsocketGateway = void 0;
const websockets_1 = require("@nestjs/websockets");
const common_1 = require("@nestjs/common");
const socket_io_1 = require("socket.io");
let WebsocketGateway = WebsocketGateway_1 = class WebsocketGateway {
    constructor() {
        this.logger = new common_1.Logger(WebsocketGateway_1.name);
        this.connectedClients = new Map();
    }
    afterInit(server) {
        this.logger.log('🔌 WebSocket Gateway inizializzato');
        this.logger.log('   Namespace: /ws');
    }
    handleConnection(client) {
        this.connectedClients.set(client.id, client);
        this.logger.log(`✅ Client connesso: ${client.id} (Total: ${this.connectedClients.size})`);
        client.emit('connection', {
            message: 'Connesso al server WebSocket',
            clientId: client.id,
            timestamp: new Date().toISOString(),
        });
    }
    handleDisconnect(client) {
        this.connectedClients.delete(client.id);
        this.logger.log(`❌ Client disconnesso: ${client.id} (Remaining: ${this.connectedClients.size})`);
    }
    async notifyEnrichmentStarted(sessionId, totalMovies) {
        const payload = {
            sessionId,
            type: 'started',
            total: totalMovies,
            processed: 0,
            percentage: 0,
            message: `Enrichment avviato per ${totalMovies} film`,
            timestamp: new Date().toISOString(),
        };
        this.server.emit('enrichment:started', payload);
        this.logger.log(`📢 Enrichment started: ${totalMovies} film (session: ${sessionId})`);
    }
    async notifyEnrichmentProgress(sessionId, processed, total, currentMovie) {
        const percentage = Math.round((processed / total) * 100);
        const payload = {
            sessionId,
            type: 'progress',
            total,
            processed,
            percentage,
            currentMovie,
            message: `Processing: ${currentMovie}`,
            timestamp: new Date().toISOString(),
        };
        this.server.emit('enrichment:progress', payload);
        if (percentage % 10 === 0 && processed > 0) {
            this.logger.log(`📊 Progress: ${processed}/${total} (${percentage}%)`);
        }
    }
    async notifyEnrichmentCompleted(sessionId, total, successful) {
        const payload = {
            sessionId,
            type: 'completed',
            total,
            processed: total,
            percentage: 100,
            successful,
            message: `Enrichment completato: ${successful}/${total} film`,
            timestamp: new Date().toISOString(),
        };
        this.server.emit('enrichment:completed', payload);
        this.logger.log(`✅ Enrichment completed: ${successful}/${total} film (session: ${sessionId})`);
    }
    async notifyEnrichmentError(sessionId, errorMessage) {
        const payload = {
            sessionId,
            type: 'error',
            message: errorMessage,
            timestamp: new Date().toISOString(),
        };
        this.server.emit('enrichment:error', payload);
        this.logger.error(`❌ Enrichment error (session: ${sessionId}): ${errorMessage}`);
    }
    broadcastMessage(event, data) {
        this.server.emit(event, data);
    }
    getConnectionInfo() {
        return {
            totalConnections: this.connectedClients.size,
            clients: Array.from(this.connectedClients.keys()),
        };
    }
};
exports.WebsocketGateway = WebsocketGateway;
__decorate([
    (0, websockets_1.WebSocketServer)(),
    __metadata("design:type", socket_io_1.Server)
], WebsocketGateway.prototype, "server", void 0);
exports.WebsocketGateway = WebsocketGateway = WebsocketGateway_1 = __decorate([
    (0, websockets_1.WebSocketGateway)({
        namespace: '/ws',
        cors: {
            origin: '*',
            credentials: true,
        },
    })
], WebsocketGateway);
//# sourceMappingURL=websocket.gateway.js.map