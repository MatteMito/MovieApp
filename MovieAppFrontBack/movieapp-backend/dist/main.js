"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
const core_1 = require("@nestjs/core");
const common_1 = require("@nestjs/common");
const app_module_1 = require("./app.module");
const config_1 = require("@nestjs/config");
const os = __importStar(require("os"));
function getLocalNetworkIP() {
    const interfaces = os.networkInterfaces();
    const priorityOrder = ['Wi-Fi', 'WiFi', 'en0', 'eth0', 'Ethernet'];
    for (const priority of priorityOrder) {
        const iface = interfaces[priority];
        if (iface) {
            for (const details of iface) {
                if (details.family === 'IPv4' && !details.internal) {
                    return details.address;
                }
            }
        }
    }
    for (const name of Object.keys(interfaces)) {
        if (name.includes('vEthernet') || name.includes('VirtualBox') || name.includes('VMware')) {
            continue;
        }
        const iface = interfaces[name];
        if (iface) {
            for (const details of iface) {
                if (details.family === 'IPv4' && !details.internal) {
                    return details.address;
                }
            }
        }
    }
    return null;
}
async function bootstrap() {
    const logger = new common_1.Logger('Bootstrap');
    const app = await core_1.NestFactory.create(app_module_1.AppModule, {
        logger: ['error', 'warn', 'log'],
    });
    const configService = app.get(config_1.ConfigService);
    app.useGlobalPipes(new common_1.ValidationPipe({
        whitelist: true,
        forbidNonWhitelisted: true,
        transform: true,
    }));
    app.enableCors({
        origin: '*',
        methods: 'GET,HEAD,PUT,PATCH,POST,DELETE,OPTIONS',
        credentials: true,
        allowedHeaders: 'Content-Type,Authorization',
    });
    app.setGlobalPrefix('');
    const port = configService.get('PORT', 3001);
    const host = configService.get('HOST', '0.0.0.0');
    await app.listen(port, host);
    const localIP = getLocalNetworkIP();
    logger.log('='.repeat(70));
    logger.log('🚀 MOVIEAPP BACKEND v2.0');
    logger.log('='.repeat(70));
    logger.log(`📊 Database: PostgreSQL | 🔐 Auth: JWT | ⚡ Cache: Attiva`);
    logger.log('');
    logger.log('📍 URL DISPONIBILI:');
    logger.log(`   Localhost:     http://localhost:${port}`);
    if (localIP) {
        logger.log(`   Network IP:    http://${localIP}:${port}`);
    }
    else {
        logger.warn('   Network IP:    Non rilevato');
    }
    logger.log('');
    logger.log('⚡ WEBSOCKET:');
    logger.log(`   Localhost:     ws://localhost:${port}/ws`);
    if (localIP) {
        logger.log(`   Network IP:    ws://${localIP}:${port}/ws`);
    }
    logger.log('');
    logger.log('📋 ENDPOINT:');
    logger.log(`   GET  /api/v1/movies/health`);
    logger.log(`   GET  /api/v1/movies/all`);
    logger.log(`   POST /api/v1/movies/enrich`);
    logger.log(`   POST /api/v1/movies/batch`);
    logger.log('');
    if (localIP) {
        logger.log(`💡 Usa questo IP per Android: ${localIP}:${port}`);
    }
    else {
        logger.warn('⚠️  IP rete locale non rilevato. Verifica WiFi.');
    }
    logger.log('='.repeat(70));
    logger.log('✅ SISTEMA PRONTO!');
    logger.log('='.repeat(70));
}
bootstrap();
//# sourceMappingURL=main.js.map