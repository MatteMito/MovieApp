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
var ListsNotificationsService_1;
Object.defineProperty(exports, "__esModule", { value: true });
exports.ListsNotificationsService = void 0;
const common_1 = require("@nestjs/common");
const typeorm_1 = require("@nestjs/typeorm");
const typeorm_2 = require("typeorm");
const schedule_1 = require("@nestjs/schedule");
const list_entity_1 = require("../../database/entities/list.entity");
const user_entity_1 = require("../../database/entities/user.entity");
let ListsNotificationsService = ListsNotificationsService_1 = class ListsNotificationsService {
    constructor(listRepository, userRepository) {
        this.listRepository = listRepository;
        this.userRepository = userRepository;
        this.logger = new common_1.Logger(ListsNotificationsService_1.name);
    }
    async checkAndSendNotifications() {
        this.logger.log('=== controllo notifiche liste schedulato ===');
        try {
            const listsToCheck = await this.listRepository.find({
                where: {
                    notifications_enabled: true,
                    target_date: (0, typeorm_2.Not)((0, typeorm_2.IsNull)()),
                },
            });
            this.logger.log(`trovate ${listsToCheck.length} liste con notifiche attive`);
            const notifications = [];
            for (const list of listsToCheck) {
                const check = await this.shouldSendNotification(list);
                if (check.shouldNotify) {
                    notifications.push(check);
                    await this.sendNotification(check);
                    await this.updateLastNotificationSent(list.id);
                }
            }
            this.logger.log(`inviate ${notifications.length} notifiche`);
        }
        catch (error) {
            this.logger.error('errore controllo notifiche', error);
        }
    }
    getRecommendedPace(totalMovies, expectedWatched, remainingDays, frequency) {
        const moviesRemaining = Math.max(0, totalMovies - expectedWatched);
        if (moviesRemaining === 0)
            return 'Tutti i film già completati';
        if (!remainingDays || remainingDays <= 0)
            return 'completare i film rimanenti';
        const filmsPerDay = moviesRemaining / remainingDays;
        const pretty = (n) => (n === 1 ? '1' : String(Math.ceil(n)));
        switch (frequency) {
            case 'daily': {
                if (filmsPerDay >= 1) {
                    return `${pretty(filmsPerDay)} film al giorno`;
                }
                const daysPerMovie = Math.ceil(remainingDays / moviesRemaining);
                return daysPerMovie === 1
                    ? '1 film al giorno'
                    : `1 film ogni ${daysPerMovie} giorni`;
            }
            case 'weekly': {
                const weeksRemaining = remainingDays / 7;
                if (weeksRemaining <= 0)
                    return 'completare i film rimanenti';
                const moviesPerWeek = moviesRemaining / weeksRemaining;
                if (moviesPerWeek >= 1) {
                    if (moviesPerWeek / 7 >= 1) {
                        return `circa ${Math.ceil(moviesPerWeek)} film a settimana (≈ ${Math.ceil(moviesPerWeek / 7)} film al giorno)`;
                    }
                    return `circa ${Math.ceil(moviesPerWeek)} film a settimana`;
                }
                const weeksPerMovie = Math.ceil(1 / moviesPerWeek);
                return `1 film ogni ${weeksPerMovie} settimane`;
            }
            case 'monthly': {
                const monthsRemaining = remainingDays / 30;
                if (monthsRemaining <= 0)
                    return 'completare i film rimanenti';
                const moviesPerMonth = moviesRemaining / monthsRemaining;
                if (moviesPerMonth >= 1) {
                    if (moviesPerMonth / 30 >= 1) {
                        return `circa ${Math.ceil(moviesPerMonth)} film al mese (≈ ${Math.ceil(moviesPerMonth / 30)} film al giorno)`;
                    }
                    return `circa ${Math.ceil(moviesPerMonth)} film al mese`;
                }
                const monthsPerMovie = Math.ceil(1 / moviesPerMonth);
                return `1 film ogni ${monthsPerMovie} mesi`;
            }
            default:
                return 'vedi quando puoi';
        }
    }
    async shouldSendNotification(list) {
        const user = await this.userRepository.findOne({
            where: { id: list.user_id },
            select: ['email', 'username'],
        });
        const now = new Date();
        const targetDate = new Date(list.target_date);
        const createdAt = new Date(list.created_at);
        now.setHours(0, 0, 0, 0);
        targetDate.setHours(0, 0, 0, 0);
        createdAt.setHours(0, 0, 0, 0);
        const totalDays = Math.ceil((targetDate.getTime() - createdAt.getTime()) / 86400000);
        const elapsedDays = Math.ceil((now.getTime() - createdAt.getTime()) / 86400000);
        const remainingDays = Math.ceil((targetDate.getTime() - now.getTime()) / 86400000);
        const totalMovies = list.movie_ids.length;
        const progressPercentage = totalDays > 0
            ? (elapsedDays / totalDays)
            : 0;
        const expectedWatched = Math.min(totalMovies, Math.floor(progressPercentage * totalMovies));
        const recommendedPace = this.getRecommendedPace(totalMovies, expectedWatched, remainingDays, list.frequency);
        const check = {
            listId: list.id,
            userId: list.user_id,
            listName: list.name,
            userEmail: user?.email || 'unknown',
            frequency: list.frequency || 'monthly',
            targetDate: list.target_date,
            moviesCount: totalMovies,
            expectedWatched,
            remainingDays,
            recommendedPace,
            shouldNotify: false,
            reason: '',
        };
        if (targetDate < now) {
            check.reason = 'target date passata, notifica disabilitata';
            return check;
        }
        if (totalMovies === 0) {
            check.reason = 'lista vuota';
            return check;
        }
        const lastSent = list.last_notification_sent
            ? new Date(list.last_notification_sent)
            : null;
        if (!lastSent) {
            check.shouldNotify = true;
            check.reason = 'prima notifica';
            return check;
        }
        const daysSinceLastNotification = (now.getTime() - lastSent.getTime()) / 86400000;
        switch (list.frequency) {
            case 'daily':
                check.shouldNotify = daysSinceLastNotification >= 1;
                check.reason = check.shouldNotify
                    ? 'notifica giornaliera'
                    : `troppo presto (${daysSinceLastNotification.toFixed(1)} giorni)`;
                break;
            case 'weekly':
                check.shouldNotify = daysSinceLastNotification >= 7;
                check.reason = check.shouldNotify
                    ? 'notifica settimanale'
                    : `troppo presto (${daysSinceLastNotification.toFixed(1)} giorni)`;
                break;
            case 'monthly':
            default:
                check.shouldNotify = daysSinceLastNotification >= 30;
                check.reason = check.shouldNotify
                    ? 'notifica mensile'
                    : `troppo presto (${daysSinceLastNotification.toFixed(1)} giorni)`;
                break;
        }
        return check;
    }
    async sendNotification(check) {
        this.logger.log(`📬 notifica inviata:`);
        this.logger.log(`  utente: ${check.userEmail}`);
        this.logger.log(`  lista: ${check.listName}`);
        this.logger.log(`  film: ${check.moviesCount}`);
        this.logger.log(`  motivo: ${check.reason}`);
        this.logger.log(`  target: ${check.targetDate.toLocaleDateString('it-IT')}`);
    }
    async updateLastNotificationSent(listId) {
        await this.listRepository.update(listId, {
            last_notification_sent: new Date(),
        });
    }
    async triggerNotificationsManually() {
        const listsToCheck = await this.listRepository.find({
            where: {
                notifications_enabled: true,
                target_date: (0, typeorm_2.Not)((0, typeorm_2.IsNull)()),
            },
        });
        const results = [];
        for (const list of listsToCheck) {
            const check = await this.shouldSendNotification(list);
            results.push(check);
        }
        return results;
    }
};
exports.ListsNotificationsService = ListsNotificationsService;
__decorate([
    (0, schedule_1.Cron)('0 9 * * *', {
        name: 'check-list-notifications',
        timeZone: 'Europe/Rome',
    }),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", []),
    __metadata("design:returntype", Promise)
], ListsNotificationsService.prototype, "checkAndSendNotifications", null);
exports.ListsNotificationsService = ListsNotificationsService = ListsNotificationsService_1 = __decorate([
    (0, common_1.Injectable)(),
    __param(0, (0, typeorm_1.InjectRepository)(list_entity_1.MovieListEntity)),
    __param(1, (0, typeorm_1.InjectRepository)(user_entity_1.UserEntity)),
    __metadata("design:paramtypes", [typeorm_2.Repository,
        typeorm_2.Repository])
], ListsNotificationsService);
//# sourceMappingURL=lists-notifications.service.js.map