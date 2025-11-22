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
exports.ListsService = void 0;
const common_1 = require("@nestjs/common");
const typeorm_1 = require("@nestjs/typeorm");
const typeorm_2 = require("typeorm");
const list_entity_1 = require("../../database/entities/list.entity");
const movie_entity_1 = require("../../database/entities/movie.entity");
const user_entity_1 = require("../../database/entities/user.entity");
let ListsService = class ListsService {
    constructor(listRepository, movieRepository, userRepository) {
        this.listRepository = listRepository;
        this.movieRepository = movieRepository;
        this.userRepository = userRepository;
    }
    async getMoviesForList(movieIds) {
        if (!movieIds || movieIds.length === 0) {
            return [];
        }
        const movies = await this.movieRepository.find({
            where: { id: (0, typeorm_2.In)(movieIds) },
        });
        const movieMap = new Map(movies.map(m => [m.id, m]));
        return movieIds
            .map(id => movieMap.get(id))
            .filter(m => m !== undefined);
    }
    async getUsernameById(userId) {
        const user = await this.userRepository.findOne({
            where: { id: userId },
            select: ['username'],
        });
        return user?.username || null;
    }
    async getPublicLists(options) {
        let query = this.listRepository
            .createQueryBuilder('list')
            .where('list.is_public = :isPublic', { isPublic: true });
        if (options.search) {
            query = query.andWhere('(list.name ILIKE :search OR list.description ILIKE :search)', { search: `%${options.search}%` });
        }
        switch (options.sortBy) {
            case 'name':
                query = query.orderBy('list.name', 'ASC');
                break;
            case 'popularity':
                query = query.orderBy('list.followers_count', 'DESC');
                break;
            case 'created':
            default:
                query = query.orderBy('list.created_at', 'DESC');
                break;
        }
        const lists = await query.getMany();
        const result = await Promise.all(lists.map(async (list) => {
            const movies = await this.getMoviesForList(list.movie_ids);
            const username = await this.getUsernameById(list.user_id);
            const isFollowing = options.userId
                ? (list.follower_ids && Array.isArray(list.follower_ids) && list.follower_ids.includes(options.userId))
                : false;
            return {
                id: list.id,
                user_id: list.user_id,
                name: list.name,
                description: list.description,
                movie_ids: list.movie_ids,
                movies: movies,
                is_public: list.is_public,
                follower_ids: list.follower_ids || [],
                followers_count: list.followers_count || 0,
                username: username,
                isFollowing: isFollowing,
                target_date: list.target_date,
                frequency: list.frequency,
                notifications_enabled: list.notifications_enabled,
                last_notification_sent: list.last_notification_sent,
                created_at: list.created_at,
                updated_at: list.updated_at,
            };
        }));
        return result;
    }
    async getUserLists(userId) {
        const lists = await this.listRepository.find({
            where: { user_id: userId },
            order: { created_at: 'DESC' },
        });
        const result = await Promise.all(lists.map(async (list) => {
            const movies = await this.getMoviesForList(list.movie_ids);
            return {
                id: list.id,
                user_id: list.user_id,
                name: list.name,
                description: list.description,
                movie_ids: list.movie_ids,
                movies: movies,
                is_public: list.is_public,
                follower_ids: list.follower_ids || [],
                followers_count: list.followers_count || 0,
                isFollowing: false,
                target_date: list.target_date,
                frequency: list.frequency,
                notifications_enabled: list.notifications_enabled,
                last_notification_sent: list.last_notification_sent,
                created_at: list.created_at,
                updated_at: list.updated_at,
            };
        }));
        return result;
    }
    async getListById(listId, userId) {
        const list = await this.listRepository.findOne({ where: { id: listId } });
        if (!list) {
            throw new common_1.NotFoundException('lista non trovata');
        }
        if (!list.is_public) {
            if (!userId || list.user_id !== userId) {
                throw new common_1.ForbiddenException('non hai accesso a questa lista privata');
            }
        }
        const movies = await this.getMoviesForList(list.movie_ids);
        const isFollowing = userId
            ? (list.follower_ids || []).includes(userId)
            : false;
        return {
            ...list,
            movies,
            isFollowing,
        };
    }
    async createList(userId, createListDto) {
        const list = this.listRepository.create({
            user_id: userId,
            name: createListDto.name,
            description: createListDto.description || null,
            is_public: createListDto.is_public || false,
            movie_ids: createListDto.movie_ids || [],
            target_date: createListDto.target_date ? new Date(createListDto.target_date) : null,
            frequency: createListDto.frequency || null,
            notifications_enabled: createListDto.notifications_enabled || false,
        });
        const saved = await this.listRepository.save(list);
        const movies = await this.getMoviesForList(saved.movie_ids);
        return {
            ...saved,
            movies,
            isFollowing: false,
        };
    }
    async updateList(listId, userId, updateListDto) {
        const list = await this.listRepository.findOne({ where: { id: listId } });
        if (!list) {
            throw new common_1.NotFoundException('lista non trovata');
        }
        if (list.user_id !== userId) {
            throw new common_1.ForbiddenException('non puoi modificare questa lista');
        }
        if (updateListDto.name !== undefined)
            list.name = updateListDto.name;
        if (updateListDto.description !== undefined)
            list.description = updateListDto.description;
        if (updateListDto.is_public !== undefined)
            list.is_public = updateListDto.is_public;
        if (updateListDto.movie_ids !== undefined)
            list.movie_ids = updateListDto.movie_ids;
        if (updateListDto.target_date !== undefined) {
            list.target_date = updateListDto.target_date ? new Date(updateListDto.target_date) : null;
        }
        if (updateListDto.frequency !== undefined)
            list.frequency = updateListDto.frequency;
        if (updateListDto.notifications_enabled !== undefined) {
            list.notifications_enabled = updateListDto.notifications_enabled;
        }
        const saved = await this.listRepository.save(list);
        const movies = await this.getMoviesForList(saved.movie_ids);
        return {
            ...saved,
            movies,
            isFollowing: false,
        };
    }
    async deleteList(listId, userId) {
        const list = await this.listRepository.findOne({ where: { id: listId } });
        if (!list) {
            throw new common_1.NotFoundException('lista non trovata');
        }
        if (list.user_id !== userId) {
            throw new common_1.ForbiddenException('non puoi eliminare questa lista');
        }
        await this.listRepository.delete(listId);
        return { message: 'lista eliminata' };
    }
    async addMovieToList(listId, userId, movieId) {
        const list = await this.listRepository.findOne({ where: { id: listId } });
        if (!list) {
            throw new common_1.NotFoundException('lista non trovata');
        }
        if (list.user_id !== userId) {
            throw new common_1.ForbiddenException('non puoi modificare questa lista');
        }
        const movie = await this.movieRepository.findOne({ where: { id: movieId } });
        if (!movie) {
            throw new common_1.NotFoundException('film non trovato');
        }
        if (list.movie_ids.includes(movieId)) {
            throw new common_1.BadRequestException('film gia presente nella lista');
        }
        list.movie_ids = [...list.movie_ids, movieId];
        const saved = await this.listRepository.save(list);
        const movies = await this.getMoviesForList(saved.movie_ids);
        return {
            ...saved,
            movies,
            isFollowing: false,
        };
    }
    async removeMovieFromList(listId, userId, movieId) {
        const list = await this.listRepository.findOne({ where: { id: listId } });
        if (!list) {
            throw new common_1.NotFoundException('lista non trovata');
        }
        if (list.user_id !== userId) {
            throw new common_1.ForbiddenException('non puoi modificare questa lista');
        }
        if (!list.movie_ids.includes(movieId)) {
            throw new common_1.BadRequestException('film non presente nella lista');
        }
        list.movie_ids = list.movie_ids.filter(id => id !== movieId);
        const saved = await this.listRepository.save(list);
        const movies = await this.getMoviesForList(saved.movie_ids);
        return {
            ...saved,
            movies,
            isFollowing: false,
        };
    }
    async followList(listId, userId) {
        const list = await this.listRepository.findOne({ where: { id: listId } });
        if (!list) {
            throw new common_1.NotFoundException('lista non trovata');
        }
        if (!list.is_public) {
            throw new common_1.ForbiddenException('puoi seguire solo liste pubbliche');
        }
        if (list.follower_ids && list.follower_ids.includes(userId)) {
            throw new common_1.BadRequestException('stai gia seguendo questa lista');
        }
        list.follower_ids = [...(list.follower_ids || []), userId];
        list.followers_count = list.follower_ids.length;
        await this.listRepository.save(list);
        return { message: 'ora segui questa lista' };
    }
    async unfollowList(listId, userId) {
        const list = await this.listRepository.findOne({ where: { id: listId } });
        if (!list) {
            throw new common_1.NotFoundException('lista non trovata');
        }
        if (!list.follower_ids || !list.follower_ids.includes(userId)) {
            throw new common_1.BadRequestException('non stai seguendo questa lista');
        }
        list.follower_ids = list.follower_ids.filter(id => id !== userId);
        list.followers_count = list.follower_ids.length;
        await this.listRepository.save(list);
        return { message: 'hai smesso di seguire questa lista' };
    }
    async getListFollowers(listId) {
        const list = await this.listRepository.findOne({ where: { id: listId } });
        if (!list) {
            throw new common_1.NotFoundException('lista non trovata');
        }
        if (!list.follower_ids || list.follower_ids.length === 0) {
            return [];
        }
        const followers = await this.userRepository.find({
            where: { id: (0, typeorm_2.In)(list.follower_ids) },
            select: ['id', 'username', 'email'],
        });
        return followers;
    }
    async copyList(listId, userId, newName) {
        const originalList = await this.listRepository.findOne({
            where: { id: listId },
        });
        if (!originalList) {
            throw new common_1.NotFoundException('lista non trovata');
        }
        if (!originalList.is_public) {
            throw new common_1.ForbiddenException('puoi copiare solo liste pubbliche');
        }
        const copiedList = this.listRepository.create({
            user_id: userId,
            name: newName || `${originalList.name} (copia)`,
            description: originalList.description,
            is_public: false,
            movie_ids: [...originalList.movie_ids],
        });
        const saved = await this.listRepository.save(copiedList);
        const movies = await this.getMoviesForList(saved.movie_ids);
        return {
            ...saved,
            movies,
            isFollowing: false,
        };
    }
};
exports.ListsService = ListsService;
exports.ListsService = ListsService = __decorate([
    (0, common_1.Injectable)(),
    __param(0, (0, typeorm_1.InjectRepository)(list_entity_1.MovieListEntity)),
    __param(1, (0, typeorm_1.InjectRepository)(movie_entity_1.MovieEntity)),
    __param(2, (0, typeorm_1.InjectRepository)(user_entity_1.UserEntity)),
    __metadata("design:paramtypes", [typeorm_2.Repository,
        typeorm_2.Repository,
        typeorm_2.Repository])
], ListsService);
//# sourceMappingURL=lists.service.js.map