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
        this.logger.log('websocket gateway inizializzato');
        this.logger.log('namespace: /ws');
    }
    handleConnection(client) {
        this.connectedClients.set(client.id, client);
        this.logger.log(`client connesso: ${client.id} (totale: ${this.connectedClients.size})`);
        client.emit('connection', {
            message: 'connesso al server websocket',
            clientId: client.id,
            timestamp: new Date().toISOString(),
        });
    }
    handleDisconnect(client) {
        this.connectedClients.delete(client.id);
        this.logger.log(`client disconnesso: ${client.id} (rimanenti: ${this.connectedClients.size})`);
    }
    async notifyEnrichmentStarted(sessionId, totalMovies) {
        const payload = {
            sessionId,
            type: 'started',
            total: totalMovies,
            processed: 0,
            percentage: 0,
            message: `enrichment avviato per ${totalMovies} film`,
            timestamp: new Date().toISOString(),
        };
        this.server.emit('enrichment:started', payload);
        this.logger.log(`enrichment started: ${totalMovies} film (session: ${sessionId})`);
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
            message: `processing: ${currentMovie}`,
            timestamp: new Date().toISOString(),
        };
        this.server.emit('enrichment:progress', payload);
        this.logger.log(`progress: ${processed}/${total} (${percentage}%) - ${currentMovie}`);
    }
    async notifyEnrichmentCompleted(sessionId, totalMovies) {
        const payload = {
            sessionId,
            type: 'completed',
            total: totalMovies,
            processed: totalMovies,
            percentage: 100,
            message: `enrichment completato: ${totalMovies} film`,
            timestamp: new Date().toISOString(),
        };
        this.server.emit('enrichment:completed', payload);
        this.logger.log(`enrichment completed: ${totalMovies} film (session: ${sessionId})`);
    }
    async notifyEnrichmentError(sessionId, error) {
        const payload = {
            sessionId,
            type: 'error',
            message: error,
            timestamp: new Date().toISOString(),
        };
        this.server.emit('enrichment:error', payload);
        this.logger.error(`enrichment error (session: ${sessionId}): ${error}`);
    }
    broadcast(event, data) {
        this.server.emit(event, data);
        this.logger.log(`broadcast: ${event}`);
    }
    sendToClient(clientId, event, data) {
        const client = this.connectedClients.get(clientId);
        if (client) {
            client.emit(event, data);
            this.logger.log(`message to ${clientId}: ${event}`);
        }
    }
    getConnectedClientsCount() {
        return this.connectedClients.size;
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