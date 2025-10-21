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
var ListsController_1;
Object.defineProperty(exports, "__esModule", { value: true });
exports.ListsController = void 0;
const common_1 = require("@nestjs/common");
const lists_service_1 = require("./lists.service");
let ListsController = ListsController_1 = class ListsController {
    constructor(listsService) {
        this.listsService = listsService;
        this.logger = new common_1.Logger(ListsController_1.name);
    }
    async createCustomList(body) {
        try {
            this.logger.log(`richiesta lista custom: ${body.name}`);
            const list = await this.listsService.createCustomList(body.name, body.filters, body.description);
            return {
                success: true,
                data: list,
                message: `lista "${body.name}" creata con ${list.totalMovies} film`,
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore lista custom: ${error.message}`);
            throw new common_1.HttpException({
                success: false,
                message: 'errore creazione lista',
                timestamp: new Date().toISOString(),
            }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async getTopRated(limit) {
        try {
            const limitNum = limit ? parseInt(limit) : 50;
            this.logger.log(`richiesta top rated (limit: ${limitNum})`);
            const list = await this.listsService.getTopRatedMovies(limitNum);
            return {
                success: true,
                data: list,
                message: `top ${list.totalMovies} film recuperati`,
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore top rated: ${error.message}`);
            throw new common_1.HttpException({
                success: false,
                message: 'errore recupero top rated',
                timestamp: new Date().toISOString(),
            }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async getRecent(limit) {
        try {
            const limitNum = limit ? parseInt(limit) : 50;
            this.logger.log(`richiesta recent movies (limit: ${limitNum})`);
            const list = await this.listsService.getRecentMovies(limitNum);
            return {
                success: true,
                data: list,
                message: `${list.totalMovies} film recenti recuperati`,
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore recent movies: ${error.message}`);
            throw new common_1.HttpException({
                success: false,
                message: 'errore recupero film recenti',
                timestamp: new Date().toISOString(),
            }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async getClassics() {
        try {
            this.logger.log('richiesta classic movies');
            const list = await this.listsService.getClassicMovies();
            return {
                success: true,
                data: list,
                message: `${list.totalMovies} film classici recuperati`,
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore classic movies: ${error.message}`);
            throw new common_1.HttpException({
                success: false,
                message: 'errore recupero film classici',
                timestamp: new Date().toISOString(),
            }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async getLong(minRuntime) {
        try {
            const minRuntimeNum = minRuntime ? parseInt(minRuntime) : 180;
            this.logger.log(`richiesta long movies (${minRuntimeNum}+ min)`);
            const list = await this.listsService.getLongMovies(minRuntimeNum);
            return {
                success: true,
                data: list,
                message: `${list.totalMovies} film lunghi recuperati`,
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore long movies: ${error.message}`);
            throw new common_1.HttpException({
                success: false,
                message: 'errore recupero film lunghi',
                timestamp: new Date().toISOString(),
            }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async getByDecade(decade) {
        try {
            const decadeNum = parseInt(decade);
            this.logger.log(`richiesta movies decade ${decadeNum}`);
            const list = await this.listsService.getMoviesByDecade(decadeNum);
            return {
                success: true,
                data: list,
                message: `${list.totalMovies} film anni ${decadeNum} recuperati`,
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore decade movies: ${error.message}`);
            throw new common_1.HttpException({
                success: false,
                message: 'errore recupero film per decade',
                timestamp: new Date().toISOString(),
            }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async getWatchlist() {
        try {
            this.logger.log('richiesta unwatched watchlist');
            const list = await this.listsService.getUnwatchedWatchlist();
            return {
                success: true,
                data: list,
                message: `${list.totalMovies} film da vedere`,
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore watchlist: ${error.message}`);
            throw new common_1.HttpException({
                success: false,
                message: 'errore recupero watchlist',
                timestamp: new Date().toISOString(),
            }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async getByGenre(genre) {
        try {
            this.logger.log(`richiesta movies genere: ${genre}`);
            const list = await this.listsService.getMoviesByGenre(genre);
            return {
                success: true,
                data: list,
                message: `${list.totalMovies} film ${genre} recuperati`,
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore genre movies: ${error.message}`);
            throw new common_1.HttpException({
                success: false,
                message: 'errore recupero film per genere',
                timestamp: new Date().toISOString(),
            }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async getByDirector(director) {
        try {
            this.logger.log(`richiesta movies regista: ${director}`);
            const list = await this.listsService.getMoviesByDirector(director);
            return {
                success: true,
                data: list,
                message: `${list.totalMovies} film di ${director} recuperati`,
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore director movies: ${error.message}`);
            throw new common_1.HttpException({
                success: false,
                message: 'errore recupero film per regista',
                timestamp: new Date().toISOString(),
            }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async getAllGenres() {
        try {
            this.logger.log('richiesta tutti i generi');
            const genres = await this.listsService.getAllGenres();
            return {
                success: true,
                data: { genres },
                message: `${genres.length} generi recuperati`,
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore generi: ${error.message}`);
            throw new common_1.HttpException({
                success: false,
                message: 'errore recupero generi',
                timestamp: new Date().toISOString(),
            }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async getAllDirectors() {
        try {
            this.logger.log('richiesta tutti i registi');
            const directors = await this.listsService.getAllDirectors();
            return {
                success: true,
                data: { directors },
                message: `${directors.length} registi recuperati`,
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore registi: ${error.message}`);
            throw new common_1.HttpException({
                success: false,
                message: 'errore recupero registi',
                timestamp: new Date().toISOString(),
            }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
};
exports.ListsController = ListsController;
__decorate([
    (0, common_1.Post)('custom'),
    __param(0, (0, common_1.Body)()),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [Object]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "createCustomList", null);
__decorate([
    (0, common_1.Get)('top-rated'),
    __param(0, (0, common_1.Query)('limit')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "getTopRated", null);
__decorate([
    (0, common_1.Get)('recent'),
    __param(0, (0, common_1.Query)('limit')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "getRecent", null);
__decorate([
    (0, common_1.Get)('classics'),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", []),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "getClassics", null);
__decorate([
    (0, common_1.Get)('long'),
    __param(0, (0, common_1.Query)('minRuntime')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "getLong", null);
__decorate([
    (0, common_1.Get)('decade/:decade'),
    __param(0, (0, common_1.Query)('decade')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "getByDecade", null);
__decorate([
    (0, common_1.Get)('watchlist'),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", []),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "getWatchlist", null);
__decorate([
    (0, common_1.Get)('genre/:genre'),
    __param(0, (0, common_1.Query)('genre')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "getByGenre", null);
__decorate([
    (0, common_1.Get)('director/:director'),
    __param(0, (0, common_1.Query)('director')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "getByDirector", null);
__decorate([
    (0, common_1.Get)('genres'),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", []),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "getAllGenres", null);
__decorate([
    (0, common_1.Get)('directors'),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", []),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "getAllDirectors", null);
exports.ListsController = ListsController = ListsController_1 = __decorate([
    (0, common_1.Controller)('api/v1/lists'),
    __metadata("design:paramtypes", [lists_service_1.ListsService])
], ListsController);
//# sourceMappingURL=lists.controller.js.map