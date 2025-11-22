// service per gestione notifiche intelligenti delle liste

import { Injectable, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository, Not, IsNull } from 'typeorm';
import { Cron } from '@nestjs/schedule';

import { MovieListEntity } from '../../database/entities/list.entity';
import { UserEntity } from '../../database/entities/user.entity';

/**
 * Struttura dei dati usata per verificare e spedire una notifica.
 */
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

  /**
   * CRON: eseguito ogni giorno alle 9 per controllare se inviare notifiche.
   */
  @Cron('0 9 * * *', {
    name: 'check-list-notifications',
    timeZone: 'Europe/Rome',
  })
  async checkAndSendNotifications(): Promise<void> {
    this.logger.log('=== controllo notifiche liste schedulato ===');

    try {
      // prendo tutte le liste che hanno le notifiche abilitate e una data target
      const listsToCheck = await this.listRepository.find({
        where: {
          notifications_enabled: true,
          target_date: Not(IsNull()),
        },
      });

      this.logger.log(`trovate ${listsToCheck.length} liste con notifiche attive`);

      const notifications: NotificationCheck[] = [];

      // loop su tutte le liste per verificare se notificare
      for (const list of listsToCheck) {
        const check = await this.shouldSendNotification(list);

        if (check.shouldNotify) {
          notifications.push(check);

          // invio notifica (placeholder)
          await this.sendNotification(check);

          // aggiorno timestamp ultima notifica
          await this.updateLastNotificationSent(list.id);
        }
      }

      this.logger.log(`inviate ${notifications.length} notifiche`);

    } catch (error) {
      this.logger.error('errore controllo notifiche', error);
    }
  }

  // funzione per calcolare il ritmo consigliato di visione
  private getRecommendedPace(
    totalMovies: number,
    expectedWatched: number,
    remainingDays: number,
    frequency: 'daily' | 'weekly' | 'monthly' | null,
  ): string {

    const moviesRemaining = Math.max(0, totalMovies - expectedWatched);

    // niente da vedere = niente ritmo
    if (moviesRemaining === 0) return 'Tutti i film già completati';

    // nessun giorno disponibile → non posso calcolare un ritmo standard
    if (!remainingDays || remainingDays <= 0) return 'completare i film rimanenti';

    // film necessari al giorno in media
    const filmsPerDay = moviesRemaining / remainingDays;

    // helper per arrotondamento
    const pretty = (n: number) => (n === 1 ? '1' : String(Math.ceil(n)));

    switch (frequency) {

      case 'daily': {
        // se la quantità per giorno è >= 1 → mostro film/giorno
        if (filmsPerDay >= 1) {
          return `${pretty(filmsPerDay)} film al giorno`;
        }

        // altrimenti → quanti giorni servono per 1 film
        const daysPerMovie = Math.ceil(remainingDays / moviesRemaining);
        return daysPerMovie === 1
          ? '1 film al giorno'
          : `1 film ogni ${daysPerMovie} giorni`;
      }

      case 'weekly': {
        const weeksRemaining = remainingDays / 7;

        if (weeksRemaining <= 0) return 'completare i film rimanenti';

        const moviesPerWeek = moviesRemaining / weeksRemaining;

        // se più di 1 a settimana → mostro anche equivalente giornaliero
        if (moviesPerWeek >= 1) {
          if (moviesPerWeek / 7 >= 1) {
            return `circa ${Math.ceil(moviesPerWeek)} film a settimana (≈ ${Math.ceil(
              moviesPerWeek / 7
            )} film al giorno)`;
          }
          return `circa ${Math.ceil(moviesPerWeek)} film a settimana`;
        }

        // se <1 a settimana → 1 film ogni X settimane
        const weeksPerMovie = Math.ceil(1 / moviesPerWeek);
        return `1 film ogni ${weeksPerMovie} settimane`;
      }

      case 'monthly': {
        const monthsRemaining = remainingDays / 30;

        if (monthsRemaining <= 0) return 'completare i film rimanenti';

        const moviesPerMonth = moviesRemaining / monthsRemaining;

        if (moviesPerMonth >= 1) {
          // se tanti → mostra anche il ritmo giornaliero
          if (moviesPerMonth / 30 >= 1) {
            return `circa ${Math.ceil(
              moviesPerMonth
            )} film al mese (≈ ${Math.ceil(
              moviesPerMonth / 30
            )} film al giorno)`;
          }
          return `circa ${Math.ceil(moviesPerMonth)} film al mese`;
        }

        // ritmo molto lento
        const monthsPerMovie = Math.ceil(1 / moviesPerMonth);
        return `1 film ogni ${monthsPerMovie} mesi`;
      }

      default:
        return 'vedi quando puoi';
    }
  }

  // logica per stabilire se inviare una notifica
  private async shouldSendNotification(
    list: MovieListEntity,
  ): Promise<NotificationCheck> {

    // recupero informazioni dell'utente
    const user = await this.userRepository.findOne({
      where: { id: list.user_id },
      select: ['email', 'username'],
    });

    const now = new Date();
    const targetDate = new Date(list.target_date);
    const createdAt = new Date(list.created_at);

    // normalizzo a mezzanotte per evitare problemi di orari
    now.setHours(0, 0, 0, 0);
    targetDate.setHours(0, 0, 0, 0);
    createdAt.setHours(0, 0, 0, 0);

    // giorni totali disponibili
    const totalDays = Math.ceil((targetDate.getTime() - createdAt.getTime()) / 86400000);

    // giorni trascorsi
    const elapsedDays = Math.ceil((now.getTime() - createdAt.getTime()) / 86400000);

    // giorni rimanenti
    const remainingDays = Math.ceil((targetDate.getTime() - now.getTime()) / 86400000);

    // film totali nella lista
    const totalMovies = list.movie_ids.length;

    // calcolo percentuale di avanzamento lineare
    const progressPercentage = totalDays > 0
      ? (elapsedDays / totalDays)
      : 0;

    // quanti film "dovrebbero" essere già stati visti
    const expectedWatched = Math.min(totalMovies, Math.floor(progressPercentage * totalMovies));

    // ottengo il ritmo consigliato tramite la funzione dedicata
    const recommendedPace = this.getRecommendedPace(
      totalMovies,
      expectedWatched,
      remainingDays,
      list.frequency as 'daily' | 'weekly' | 'monthly'
    );

    // struttura base del risultato
    const check: NotificationCheck = {
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

    // se data target già superata → stop
    if (targetDate < now) {
      check.reason = 'target date passata, notifica disabilitata';
      return check;
    }

    // lista vuota → nessuna notifica
    if (totalMovies === 0) {
      check.reason = 'lista vuota';
      return check;
    }

    const lastSent = list.last_notification_sent
      ? new Date(list.last_notification_sent)
      : null;

    // se nessuna notifica inviata → invia la prima
    if (!lastSent) {
      check.shouldNotify = true;
      check.reason = 'prima notifica';
      return check;
    }

    // calcolo giorni dall’ultima notifica
    const daysSinceLastNotification =
      (now.getTime() - lastSent.getTime()) / 86400000;

    // controllo frequenza scelta dall'utente
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

  /**
   * Placeholder: qui potrai integrare un vero servizio email / push.
   */
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

  /**
   * Aggiorna il timestamp dell’ultima notifica inviata.
   */
  private async updateLastNotificationSent(listId: string): Promise<void> {
    await this.listRepository.update(listId, {
      last_notification_sent: new Date(),
    });
  }

  /**
   * Endpoint manuale per debugging e testing delle notifiche.
   */
  async triggerNotificationsManually(): Promise<NotificationCheck[]> {
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