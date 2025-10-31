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
var __param = (this && this.__param) || function (paramIndex, decorator) {
    return function (target, key) { decorator(target, key, paramIndex); }
};
var AnalyticsController_1;
Object.defineProperty(exports, "__esModule", { value: true });
exports.AnalyticsController = void 0;
const common_1 = require("@nestjs/common");
const analytics_service_1 = require("./analytics.service");
let AnalyticsController = AnalyticsController_1 = class AnalyticsController {
    constructor(analyticsService) {
        this.analyticsService = analyticsService;
        this.logger = new common_1.Logger(AnalyticsController_1.name);
    }
    async getCompleteAnalytics(userId) {
        try {
            if (!userId) {
                throw new common_1.HttpException({
                    success: false,
                    message: 'userId mancante',
                    timestamp: new Date().toISOString()
                }, common_1.HttpStatus.BAD_REQUEST);
            }
            this.logger.log(`📊 analytics complete richieste per utente ${userId}`);
            const startTime = Date.now();
            const analytics = await this.analyticsService.getCompleteAnalytics(userId);
            const elapsed = Date.now() - startTime;
            this.logger.log(`✅ analytics generate in ${elapsed}ms`);
            return {
                success: true,
                data: analytics,
                message: `analytics complete per ${analytics.basicStats.totalMovies} film (${analytics.basicStats.watchedCount} watched, ${analytics.basicStats.watchlistCount} watchlist)`,
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`❌ errore analytics complete: ${error.message}`);
            throw new common_1.HttpException({
                success: false,
                message: error.message,
                timestamp: new Date().toISOString()
            }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async getBasicStats(userId) {
        try {
            if (!userId) {
                throw new common_1.HttpException({ success: false, message: 'userId mancante', timestamp: new Date().toISOString() }, common_1.HttpStatus.BAD_REQUEST);
            }
            this.logger.log(`📊 statistiche base richieste per utente ${userId}`);
            const stats = await this.analyticsService.getBasicStats(userId);
            return {
                success: true,
                data: stats,
                message: 'statistiche base recuperate',
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore statistiche base: ${error.message}`);
            throw new common_1.HttpException({ success: false, message: error.message, timestamp: new Date().toISOString() }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async getGenreStats(userId, limit) {
        try {
            if (!userId) {
                throw new common_1.HttpException({ success: false, message: 'userId mancante', timestamp: new Date().toISOString() }, common_1.HttpStatus.BAD_REQUEST);
            }
            const limitNum = limit ? parseInt(limit, 10) : 10;
            this.logger.log(`📊 statistiche generi per utente ${userId}`);
            const stats = await this.analyticsService.getGenreStats(userId);
            const limitedStats = stats.slice(0, limitNum);
            return {
                success: true,
                data: limitedStats,
                message: `top ${limitedStats.length} generi`,
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore statistiche generi: ${error.message}`);
            throw new common_1.HttpException({ success: false, message: error.message, timestamp: new Date().toISOString() }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async getDirectorStats(userId, limit) {
        try {
            if (!userId) {
                throw new common_1.HttpException({ success: false, message: 'userId mancante', timestamp: new Date().toISOString() }, common_1.HttpStatus.BAD_REQUEST);
            }
            const limitNum = limit ? parseInt(limit, 10) : 10;
            this.logger.log(`📊 statistiche registi per utente ${userId}`);
            const stats = await this.analyticsService.getDirectorStats(userId);
            const limitedStats = stats.slice(0, limitNum);
            return {
                success: true,
                data: limitedStats,
                message: `top ${limitedStats.length} registi`,
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore statistiche registi: ${error.message}`);
            throw new common_1.HttpException({ success: false, message: error.message, timestamp: new Date().toISOString() }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async getAdvancedAnalytics(userId) {
        try {
            if (!userId) {
                throw new common_1.HttpException({ success: false, message: 'userId mancante', timestamp: new Date().toISOString() }, common_1.HttpStatus.BAD_REQUEST);
            }
            this.logger.log(`📊 analytics avanzate per utente ${userId}`);
            const analytics = await this.analyticsService.getAdvancedAnalytics(userId);
            return {
                success: true,
                data: analytics,
                message: 'analytics complete',
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore analytics avanzate: ${error.message}`);
            throw new common_1.HttpException({ success: false, message: error.message, timestamp: new Date().toISOString() }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
};
exports.AnalyticsController = AnalyticsController;
__decorate([
    (0, common_1.Get)('user/:userId'),
    __param(0, (0, common_1.Param)('userId')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String]),
    __metadata("design:returntype", Promise)
], AnalyticsController.prototype, "getCompleteAnalytics", null);
__decorate([
    (0, common_1.Get)('basic'),
    __param(0, (0, common_1.Query)('userId')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String]),
    __metadata("design:returntype", Promise)
], AnalyticsController.prototype, "getBasicStats", null);
__decorate([
    (0, common_1.Get)('genres'),
    __param(0, (0, common_1.Query)('userId')),
    __param(1, (0, common_1.Query)('limit')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String, String]),
    __metadata("design:returntype", Promise)
], AnalyticsController.prototype, "getGenreStats", null);
__decorate([
    (0, common_1.Get)('directors'),
    __param(0, (0, common_1.Query)('userId')),
    __param(1, (0, common_1.Query)('limit')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String, String]),
    __metadata("design:returntype", Promise)
], AnalyticsController.prototype, "getDirectorStats", null);
__decorate([
    (0, common_1.Get)('advanced'),
    __param(0, (0, common_1.Query)('userId')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String]),
    __metadata("design:returntype", Promise)
], AnalyticsController.prototype, "getAdvancedAnalytics", null);
exports.AnalyticsController = AnalyticsController = AnalyticsController_1 = __decorate([
    (0, common_1.Controller)('api/v1/analytics'),
    __metadata("design:paramtypes", [analytics_service_1.AnalyticsService])
], AnalyticsController);
//# sourceMappingURL=analytics.controller.js.map