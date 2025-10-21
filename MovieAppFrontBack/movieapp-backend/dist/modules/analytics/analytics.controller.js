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
    async getBasicStats() {
        try {
            this.logger.log('richiesta stats base');
            const stats = await this.analyticsService.getBasicStats();
            return {
                success: true,
                data: stats,
                message: 'statistiche base recuperate',
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore stats base: ${error.message}`);
            throw new common_1.HttpException({
                success: false,
                message: 'errore recupero statistiche',
                timestamp: new Date().toISOString(),
            }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async getGenreStats(limit) {
        try {
            const limitNum = limit ? parseInt(limit) : 10;
            this.logger.log(`richiesta stats generi (limit: ${limitNum})`);
            const stats = await this.analyticsService.getGenreStats(limitNum);
            return {
                success: true,
                data: stats,
                message: `top ${stats.length} generi recuperati`,
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore stats generi: ${error.message}`);
            throw new common_1.HttpException({
                success: false,
                message: 'errore recupero statistiche generi',
                timestamp: new Date().toISOString(),
            }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async getYearStats() {
        try {
            this.logger.log('richiesta stats anni');
            const stats = await this.analyticsService.getYearStats();
            return {
                success: true,
                data: stats,
                message: `statistiche ${stats.length} anni recuperate`,
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore stats anni: ${error.message}`);
            throw new common_1.HttpException({
                success: false,
                message: 'errore recupero statistiche anni',
                timestamp: new Date().toISOString(),
            }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async getDirectorStats(limit) {
        try {
            const limitNum = limit ? parseInt(limit) : 10;
            this.logger.log(`richiesta stats registi (limit: ${limitNum})`);
            const stats = await this.analyticsService.getDirectorStats(limitNum);
            return {
                success: true,
                data: stats,
                message: `top ${stats.length} registi recuperati`,
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore stats registi: ${error.message}`);
            throw new common_1.HttpException({
                success: false,
                message: 'errore recupero statistiche registi',
                timestamp: new Date().toISOString(),
            }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async getRatingDistribution() {
        try {
            this.logger.log('richiesta distribuzione rating');
            const distribution = await this.analyticsService.getRatingDistribution();
            return {
                success: true,
                data: distribution,
                message: 'distribuzione rating recuperata',
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore distribuzione rating: ${error.message}`);
            throw new common_1.HttpException({
                success: false,
                message: 'errore recupero distribuzione rating',
                timestamp: new Date().toISOString(),
            }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async getDecadeDistribution() {
        try {
            this.logger.log('richiesta distribuzione decadi');
            const distribution = await this.analyticsService.getDecadeDistribution();
            return {
                success: true,
                data: distribution,
                message: 'distribuzione decadi recuperata',
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore distribuzione decadi: ${error.message}`);
            throw new common_1.HttpException({
                success: false,
                message: 'errore recupero distribuzione decadi',
                timestamp: new Date().toISOString(),
            }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async getAdvancedAnalytics() {
        try {
            this.logger.log('richiesta analytics avanzate');
            const analytics = await this.analyticsService.getAdvancedAnalytics();
            return {
                success: true,
                data: analytics,
                message: 'analytics avanzate recuperate',
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore analytics avanzate: ${error.message}`);
            throw new common_1.HttpException({
                success: false,
                message: 'errore recupero analytics avanzate',
                timestamp: new Date().toISOString(),
            }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async getTextReport() {
        try {
            this.logger.log('richiesta report testuale');
            const report = await this.analyticsService.generateTextReport();
            return {
                success: true,
                data: { report },
                message: 'report generato',
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore report: ${error.message}`);
            throw new common_1.HttpException({
                success: false,
                message: 'errore generazione report',
                timestamp: new Date().toISOString(),
            }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
};
exports.AnalyticsController = AnalyticsController;
__decorate([
    (0, common_1.Get)('basic'),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", []),
    __metadata("design:returntype", Promise)
], AnalyticsController.prototype, "getBasicStats", null);
__decorate([
    (0, common_1.Get)('genres'),
    __param(0, (0, common_1.Query)('limit')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String]),
    __metadata("design:returntype", Promise)
], AnalyticsController.prototype, "getGenreStats", null);
__decorate([
    (0, common_1.Get)('years'),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", []),
    __metadata("design:returntype", Promise)
], AnalyticsController.prototype, "getYearStats", null);
__decorate([
    (0, common_1.Get)('directors'),
    __param(0, (0, common_1.Query)('limit')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String]),
    __metadata("design:returntype", Promise)
], AnalyticsController.prototype, "getDirectorStats", null);
__decorate([
    (0, common_1.Get)('rating-distribution'),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", []),
    __metadata("design:returntype", Promise)
], AnalyticsController.prototype, "getRatingDistribution", null);
__decorate([
    (0, common_1.Get)('decade-distribution'),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", []),
    __metadata("design:returntype", Promise)
], AnalyticsController.prototype, "getDecadeDistribution", null);
__decorate([
    (0, common_1.Get)('advanced'),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", []),
    __metadata("design:returntype", Promise)
], AnalyticsController.prototype, "getAdvancedAnalytics", null);
__decorate([
    (0, common_1.Get)('report'),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", []),
    __metadata("design:returntype", Promise)
], AnalyticsController.prototype, "getTextReport", null);
exports.AnalyticsController = AnalyticsController = AnalyticsController_1 = __decorate([
    (0, common_1.Controller)('api/v1/analytics'),
    __metadata("design:paramtypes", [analytics_service_1.AnalyticsService])
], AnalyticsController);
//# sourceMappingURL=analytics.controller.js.map