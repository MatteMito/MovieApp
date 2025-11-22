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
const lists_notifications_service_1 = require("./lists-notifications.service");
const list_dto_1 = require("../../common/dto/list.dto");
let ListsController = ListsController_1 = class ListsController {
    constructor(listsService, notificationsService) {
        this.listsService = listsService;
        this.notificationsService = notificationsService;
        this.logger = new common_1.Logger(ListsController_1.name);
    }
    async getPublicLists(search, sortBy, limit, userId) {
        this.logger.log(`getPublicLists chiamato con userId=${userId}`);
        const parsedLimit = limit ? parseInt(limit, 10) : 20;
        return this.listsService.getPublicLists({ search, sortBy, userId });
    }
    async getMyLists(userId) {
        if (!userId) {
            throw new common_1.HttpException('userId richiesto', common_1.HttpStatus.BAD_REQUEST);
        }
        return this.listsService.getUserLists(userId);
    }
    async getListById(id, userId) {
        return this.listsService.getListById(id, userId);
    }
    async createList(createListDto) {
        const userId = createListDto.user_id;
        if (!userId) {
            throw new common_1.HttpException('user_id richiesto', common_1.HttpStatus.BAD_REQUEST);
        }
        return this.listsService.createList(userId, createListDto);
    }
    async updateList(id, updateListDto, userId) {
        if (!userId) {
            throw new common_1.HttpException('userId richiesto', common_1.HttpStatus.BAD_REQUEST);
        }
        return this.listsService.updateList(id, userId, updateListDto);
    }
    async deleteList(id, userId) {
        if (!userId) {
            throw new common_1.HttpException('userId richiesto', common_1.HttpStatus.BAD_REQUEST);
        }
        return this.listsService.deleteList(id, userId);
    }
    async addMovieToList(listId, addMovieDto, userId) {
        if (!userId) {
            throw new common_1.HttpException('userId richiesto', common_1.HttpStatus.BAD_REQUEST);
        }
        return this.listsService.addMovieToList(listId, userId, addMovieDto.movie_id);
    }
    async removeMovieFromList(listId, movieId, userId) {
        if (!userId) {
            throw new common_1.HttpException('userId richiesto', common_1.HttpStatus.BAD_REQUEST);
        }
        return this.listsService.removeMovieFromList(listId, userId, movieId);
    }
    async followList(listId, body) {
        const userId = body.userId;
        if (!userId) {
            throw new common_1.HttpException('userId richiesto', common_1.HttpStatus.BAD_REQUEST);
        }
        return this.listsService.followList(listId, userId);
    }
    async unfollowList(listId, userId) {
        if (!userId) {
            throw new common_1.HttpException('userId richiesto', common_1.HttpStatus.BAD_REQUEST);
        }
        return this.listsService.unfollowList(listId, userId);
    }
    async getListFollowers(listId) {
        return this.listsService.getListFollowers(listId);
    }
    async copyList(listId, body) {
        const userId = body.userId;
        if (!userId) {
            throw new common_1.HttpException('userId richiesto', common_1.HttpStatus.BAD_REQUEST);
        }
        return this.listsService.copyList(listId, userId, body.newName);
    }
    async checkNotifications() {
        return this.notificationsService.triggerNotificationsManually();
    }
};
exports.ListsController = ListsController;
__decorate([
    (0, common_1.Get)('public'),
    __param(0, (0, common_1.Query)('search')),
    __param(1, (0, common_1.Query)('sortBy')),
    __param(2, (0, common_1.Query)('limit')),
    __param(3, (0, common_1.Query)('userId')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String, String, String, String]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "getPublicLists", null);
__decorate([
    (0, common_1.Get)('my'),
    __param(0, (0, common_1.Query)('userId')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "getMyLists", null);
__decorate([
    (0, common_1.Get)(':id'),
    __param(0, (0, common_1.Param)('id')),
    __param(1, (0, common_1.Query)('userId')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String, String]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "getListById", null);
__decorate([
    (0, common_1.Post)(),
    __param(0, (0, common_1.Body)()),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [list_dto_1.CreateListDto]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "createList", null);
__decorate([
    (0, common_1.Put)(':id'),
    __param(0, (0, common_1.Param)('id')),
    __param(1, (0, common_1.Body)()),
    __param(2, (0, common_1.Query)('userId')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String, list_dto_1.UpdateListDto, String]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "updateList", null);
__decorate([
    (0, common_1.Delete)(':id'),
    __param(0, (0, common_1.Param)('id')),
    __param(1, (0, common_1.Query)('userId')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String, String]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "deleteList", null);
__decorate([
    (0, common_1.Post)(':id/movies'),
    __param(0, (0, common_1.Param)('id')),
    __param(1, (0, common_1.Body)()),
    __param(2, (0, common_1.Query)('userId')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String, list_dto_1.AddMovieToListDto, String]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "addMovieToList", null);
__decorate([
    (0, common_1.Delete)(':id/movies/:movieId'),
    __param(0, (0, common_1.Param)('id')),
    __param(1, (0, common_1.Param)('movieId')),
    __param(2, (0, common_1.Query)('userId')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String, String, String]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "removeMovieFromList", null);
__decorate([
    (0, common_1.Post)(':id/follow'),
    __param(0, (0, common_1.Param)('id')),
    __param(1, (0, common_1.Body)()),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String, Object]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "followList", null);
__decorate([
    (0, common_1.Delete)(':id/follow'),
    __param(0, (0, common_1.Param)('id')),
    __param(1, (0, common_1.Query)('userId')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String, String]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "unfollowList", null);
__decorate([
    (0, common_1.Get)(':id/followers'),
    __param(0, (0, common_1.Param)('id')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "getListFollowers", null);
__decorate([
    (0, common_1.Post)(':id/copy'),
    __param(0, (0, common_1.Param)('id')),
    __param(1, (0, common_1.Body)()),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String, Object]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "copyList", null);
__decorate([
    (0, common_1.Get)('notifications/check'),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", []),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "checkNotifications", null);
exports.ListsController = ListsController = ListsController_1 = __decorate([
    (0, common_1.Controller)('api/v1/lists'),
    __metadata("design:paramtypes", [lists_service_1.ListsService,
        lists_notifications_service_1.ListsNotificationsService])
], ListsController);
//# sourceMappingURL=lists.controller.js.map