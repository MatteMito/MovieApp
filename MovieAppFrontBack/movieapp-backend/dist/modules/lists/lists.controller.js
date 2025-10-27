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
Object.defineProperty(exports, "__esModule", { value: true });
exports.ListsController = void 0;
const common_1 = require("@nestjs/common");
const jwt_auth_guard_1 = require("../../common/guards/jwt-auth.guard");
const lists_service_1 = require("./lists.service");
const list_dto_1 = require("../../common/dto/list.dto");
let ListsController = class ListsController {
    constructor(listsService) {
        this.listsService = listsService;
    }
    async getMyLists(req) {
        const userId = req.user?.userId;
        if (!userId) {
            throw new common_1.HttpException('User ID non trovato', common_1.HttpStatus.UNAUTHORIZED);
        }
        return this.listsService.getUserLists(userId);
    }
    async getPublicLists(search, sortBy) {
        return this.listsService.getPublicLists({ search, sortBy });
    }
    async getListById(id, req) {
        const userId = req.user?.userId;
        return this.listsService.getListById(id, userId);
    }
    async createList(createListDto, req) {
        const userId = req.user?.userId;
        if (!userId) {
            throw new common_1.HttpException('User ID non trovato', common_1.HttpStatus.UNAUTHORIZED);
        }
        return this.listsService.createList(userId, createListDto);
    }
    async updateList(id, updateListDto, req) {
        const userId = req.user?.userId;
        if (!userId) {
            throw new common_1.HttpException('User ID non trovato', common_1.HttpStatus.UNAUTHORIZED);
        }
        return this.listsService.updateList(id, userId, updateListDto);
    }
    async deleteList(id, req) {
        const userId = req.user?.userId;
        if (!userId) {
            throw new common_1.HttpException('User ID non trovato', common_1.HttpStatus.UNAUTHORIZED);
        }
        return this.listsService.deleteList(id, userId);
    }
    async addMovieToList(listId, addMovieDto, req) {
        const userId = req.user?.userId;
        if (!userId) {
            throw new common_1.HttpException('User ID non trovato', common_1.HttpStatus.UNAUTHORIZED);
        }
        return this.listsService.addMovieToList(listId, userId, addMovieDto.movie_id);
    }
    async removeMovieFromList(listId, movieId, req) {
        const userId = req.user?.userId;
        if (!userId) {
            throw new common_1.HttpException('User ID non trovato', common_1.HttpStatus.UNAUTHORIZED);
        }
        return this.listsService.removeMovieFromList(listId, userId, movieId);
    }
    async followList(listId, req) {
        const userId = req.user?.userId;
        if (!userId) {
            throw new common_1.HttpException('User ID non trovato', common_1.HttpStatus.UNAUTHORIZED);
        }
        return this.listsService.followList(listId, userId);
    }
    async unfollowList(listId, req) {
        const userId = req.user?.userId;
        if (!userId) {
            throw new common_1.HttpException('User ID non trovato', common_1.HttpStatus.UNAUTHORIZED);
        }
        return this.listsService.unfollowList(listId, userId);
    }
    async getListFollowers(listId) {
        return this.listsService.getListFollowers(listId);
    }
};
exports.ListsController = ListsController;
__decorate([
    (0, common_1.Get)('my'),
    __param(0, (0, common_1.Request)()),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [Object]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "getMyLists", null);
__decorate([
    (0, common_1.Get)('public'),
    __param(0, (0, common_1.Query)('search')),
    __param(1, (0, common_1.Query)('sortBy')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String, String]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "getPublicLists", null);
__decorate([
    (0, common_1.Get)(':id'),
    __param(0, (0, common_1.Param)('id')),
    __param(1, (0, common_1.Request)()),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String, Object]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "getListById", null);
__decorate([
    (0, common_1.Post)(),
    __param(0, (0, common_1.Body)()),
    __param(1, (0, common_1.Request)()),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [list_dto_1.CreateListDto, Object]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "createList", null);
__decorate([
    (0, common_1.Put)(':id'),
    __param(0, (0, common_1.Param)('id')),
    __param(1, (0, common_1.Body)()),
    __param(2, (0, common_1.Request)()),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String, list_dto_1.UpdateListDto, Object]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "updateList", null);
__decorate([
    (0, common_1.Delete)(':id'),
    __param(0, (0, common_1.Param)('id')),
    __param(1, (0, common_1.Request)()),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String, Object]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "deleteList", null);
__decorate([
    (0, common_1.Post)(':id/movies'),
    __param(0, (0, common_1.Param)('id')),
    __param(1, (0, common_1.Body)()),
    __param(2, (0, common_1.Request)()),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String, list_dto_1.AddMovieToListDto, Object]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "addMovieToList", null);
__decorate([
    (0, common_1.Delete)(':id/movies/:movieId'),
    __param(0, (0, common_1.Param)('id')),
    __param(1, (0, common_1.Param)('movieId')),
    __param(2, (0, common_1.Request)()),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String, String, Object]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "removeMovieFromList", null);
__decorate([
    (0, common_1.Post)(':id/follow'),
    __param(0, (0, common_1.Param)('id')),
    __param(1, (0, common_1.Request)()),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String, Object]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "followList", null);
__decorate([
    (0, common_1.Delete)(':id/follow'),
    __param(0, (0, common_1.Param)('id')),
    __param(1, (0, common_1.Request)()),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String, Object]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "unfollowList", null);
__decorate([
    (0, common_1.Get)(':id/followers'),
    __param(0, (0, common_1.Param)('id')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "getListFollowers", null);
exports.ListsController = ListsController = __decorate([
    (0, common_1.Controller)('api/v1/lists'),
    (0, common_1.UseGuards)(jwt_auth_guard_1.JwtAuthGuard),
    __metadata("design:paramtypes", [lists_service_1.ListsService])
], ListsController);
//# sourceMappingURL=lists.controller.js.map