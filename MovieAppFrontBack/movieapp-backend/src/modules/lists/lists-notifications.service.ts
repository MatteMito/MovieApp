// service per gestione notifiche intelligenti delle liste

import { Injectable, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository, LessThanOrEqual, IsNull, Not } from 'typeorm';
import { Cron, CronExpression } from '@nestjs/schedule';
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

@Injectable()
export class ListsNotificationsService {
  private readonly logger = new Logger(ListsNotificationsService.name);

  constructor(
    @InjectRepository(MovieListEntity)
    private readonly listRepository: Repository<MovieListEntity>,

    @InjectRepository(UserEntity)
    private readonly userRepository: Repository<UserEntity>,
  ) {}

  // cron job che gira ogni giorno alle 9:00 per controllare le notifiche
  @Cron('0 9 * * *', {
    name: 'check-list-notifications',
    timeZone: 'Europe/Rome',
  })
  async checkAndSendNotifications(): Promise<void> {
    this.logger.log('=== controllo notifiche liste schedulato ===');

    try {
      // trova liste con notifiche abilitate
      const listsToCheck = await this.listRepository.find({
        where: {
          notifications_enabled: true,
          target_date: Not(IsNull()),
        },
      });

      this.logger.log(`trovate ${listsToCheck.length} liste con notifiche attive`);

      const notifications: NotificationCheck[] = [];

      for (const list of listsToCheck) {
        const check = await this.shouldSendNotification(list);
        if (check.shouldNotify) {
          notifications.push(check);
          await this.sendNotification(check);
          await this.updateLastNotificationSent(list.id);
        }
      }

      this.logger.log(`inviate ${notifications.length} notifiche`);

    } catch (error) {
      this.logger.error('errore controllo notifiche', error);
    }
  }

  // determina se inviare notifica basandosi su frequency e ultima notifica
    private async shouldSendNotification(
    list: MovieListEntity,
    ): Promise<NotificationCheck> {
    const user = await this.userRepository.findOne({
        where: { id: list.user_id },
        select: ['email', 'username'],
    });

    const now = new Date();
    const targetDate = new Date(list.target_date);
    const createdAt = new Date(list.created_at);

    // normalizza le date a mezzanotte per calcolo accurato
    now.setHours(0, 0, 0, 0);
    targetDate.setHours(0, 0, 0, 0);
    createdAt.setHours(0, 0, 0, 0);

    // calcola giorni totali e giorni trascorsi
    const totalDays = Math.ceil((targetDate.getTime() - createdAt.getTime()) / (1000 * 60 * 60 * 24));
    const elapsedDays = Math.ceil((now.getTime() - createdAt.getTime()) / (1000 * 60 * 60 * 24));
    const remainingDays = Math.ceil((targetDate.getTime() - now.getTime()) / (1000 * 60 * 60 * 24));

    // calcola quanti film dovrebbero essere stati visti
    const totalMovies = list.movie_ids.length;
    const progressPercentage = totalDays > 0 ? (elapsedDays / totalDays) : 0;
    const expectedWatched = Math.min(totalMovies, Math.floor(progressPercentage * totalMovies));

    // calcola ritmo consigliato (film per unità di tempo)
    let recommendedPace = '';
    switch (list.frequency) {
        case 'daily':
        recommendedPace = 'circa 1 film al giorno';
        break;
        case 'weekly':
        const weeksRemaining = Math.ceil(remainingDays / 7);
        const moviesPerWeek = weeksRemaining > 0 ? Math.ceil(totalMovies / (totalDays / 7)) : totalMovies;
        recommendedPace = `circa ${moviesPerWeek} film a settimana`;
        break;
        case 'monthly':
        const monthsRemaining = Math.ceil(remainingDays / 30);
        const moviesPerMonth = monthsRemaining > 0 ? Math.ceil(totalMovies / (totalDays / 30)) : totalMovies;
        recommendedPace = `circa ${moviesPerMonth} film al mese`;
        break;
    }

    const check: NotificationCheck = {
        listId: list.id,
        userId: list.user_id,
        listName: list.name,
        userEmail: user?.email || 'unknown',
        frequency: list.frequency || 'monthly',
        targetDate: list.target_date,
        moviesCount: totalMovies,
        expectedWatched: expectedWatched,
        remainingDays: remainingDays,
        recommendedPace: recommendedPace,
        shouldNotify: false,
        reason: '',
    };

    // check 1: target date passata
    if (targetDate < now) {
        check.reason = 'target date passata, notifica disabilitata';
        return check;
    }

    // check 2: nessun film nella lista
    if (list.movie_ids.length === 0) {
        check.reason = 'lista vuota';
        return check;
    }

    // check 3: frequenza notifica
    const lastSent = list.last_notification_sent
        ? new Date(list.last_notification_sent)
        : null;

    if (!lastSent) {
        // prima notifica
        check.shouldNotify = true;
        check.reason = 'prima notifica';
        return check;
    }

    // calcola giorni dalla ultima notifica
    const daysSinceLastNotification =
        (now.getTime() - lastSent.getTime()) / (1000 * 60 * 60 * 24);

    switch (list.frequency) {
        case 'daily':
        check.shouldNotify = daysSinceLastNotification >= 0.01;
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

  // invia notifica (placeholder per integrazione futura con servizio email/push)
  private async sendNotification(check: NotificationCheck): Promise<void> {
    this.logger.log(`📬 notifica inviata:`);
    this.logger.log(`  utente: ${check.userEmail}`);
    this.logger.log(`  lista: ${check.listName}`);
    this.logger.log(`  film: ${check.moviesCount}`);
    this.logger.log(`  motivo: ${check.reason}`);
    this.logger.log(
      `  target: ${check.targetDate.toLocaleDateString('it-IT')}`,
    );
  }

  // aggiorna timestamp ultima notifica inviata
  private async updateLastNotificationSent(listId: string): Promise<void> {
    await this.listRepository.update(listId, {
      last_notification_sent: new Date(),
    });
  }

  // endpoint manuale per testare notifiche
  async triggerNotificationsManually(): Promise<NotificationCheck[]> {
    this.logger.log('trigger manuale notifiche');

    const listsToCheck = await this.listRepository.find({
      where: {
        notifications_enabled: true,
        target_date: Not(IsNull()),
      },
    });

    const results: NotificationCheck[] = [];

    for (const list of listsToCheck) {
      const check = await this.shouldSendNotification(list);
      results.push(check);
    }

    return results;
  }
}