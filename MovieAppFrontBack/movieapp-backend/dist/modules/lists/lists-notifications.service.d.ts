import { Repository } from 'typeorm';
import { MovieListEntity } from '../../database/entities/list.entity';
import { UserEntity } from '../../database/entities/user.entity';
export interface NotificationCheck {
    listId: string;
    userId: string;
    listName: string;
    userEmail: string;
    frequency: string;
    targetDate: Date;
    moviesCount: number;
    expectedWatched: number;
    remainingDays: number;
    recommendedPace: string;
    shouldNotify: boolean;
    reason: string;
}
export declare class ListsNotificationsService {
    private readonly listRepository;
    private readonly userRepository;
    private readonly logger;
    constructor(listRepository: Repository<MovieListEntity>, userRepository: Repository<UserEntity>);
    checkAndSendNotifications(): Promise<void>;
    private getRecommendedPace;
    private shouldSendNotification;
    private sendNotification;
    private updateLastNotificationSent;
    triggerNotificationsManually(): Promise<NotificationCheck[]>;
}
